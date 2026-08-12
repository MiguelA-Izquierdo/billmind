package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The role beans are the single place where "which model serves this call" is decided, and the
 * fallback to the provider's model is an expression in application.properties — so the file on
 * the classpath is loaded here rather than restated, which is what puts the expression itself
 * under test instead of a copy of it that cannot drift.
 */
class ChatModelRolesConfigTest {

    /** Model names the factory was asked for, in bean-creation order. */
    private static final List<String> requested = new ArrayList<>();

    /** Output ceiling the factory was asked for, per model — so a swap between roles is visible. */
    private static final Map<String, Integer> capsByModel = new HashMap<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(ChatModelRolesConfig.class, StubFactoryConfig.class)
            .withInitializer(ctx -> {
                requested.clear();
                capsByModel.clear();
                ctx.getEnvironment().getPropertySources().addLast(applicationProperties());
            });

    /** Added last, so a property named by a test still wins over the shipped file. */
    private static ResourcePropertySource applicationProperties() {
        try {
            return new ResourcePropertySource(new ClassPathResource("application.properties"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void shouldUseProviderModelForBothRolesWhenNoRoleOverrideIsSet() {
        runner.withPropertyValues("llm.provider=groq", "llm.groq.model=openai/gpt-oss-120b")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "openai/gpt-oss-120b", "openai/gpt-oss-120b");
                });
    }

    @Test
    void shouldGiveEachRoleItsOwnModelWhenBothOverridesAreSet() {
        runner.withPropertyValues(
                        "llm.provider=groq",
                        "llm.groq.model=openai/gpt-oss-120b",
                        "llm.role.fast.model=openai/gpt-oss-20b",
                        "llm.role.smart.model=openai/gpt-oss-120b")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "openai/gpt-oss-20b", "openai/gpt-oss-120b");
                });
    }

    @Test
    void shouldOverrideOnlyTheRoleThatDeclaresItsOwnModel() {
        runner.withPropertyValues(
                        "llm.provider=groq",
                        "llm.groq.model=openai/gpt-oss-120b",
                        "llm.role.fast.model=openai/gpt-oss-20b")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "openai/gpt-oss-20b", "openai/gpt-oss-120b");
                });
    }

    /**
     * The cap belongs to the model, not to the request: the Anthropic integration rejects a
     * per-request maxOutputTokens, so a role built without it would silently take the
     * provider default (1024 on Anthropic) and truncate the longest answers.
     *
     * <p>And it is per role: the two answer different things, and a cap that fits one truncates
     * the other — a reasoning model cut mid-JSON degrades silently wherever the answer is parsed.
     */
    @Test
    void shouldHandEachRoleItsOwnOutputCap() {
        runner.withPropertyValues(
                        "llm.provider=groq",
                        "llm.groq.model=openai/gpt-oss-120b",
                        "llm.role.fast.model=a-fast-model",
                        "llm.role.smart.model=a-smart-model")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(capsByModel).containsOnly(
                            entry("a-fast-model",  ChatModelRolesConfig.FAST_MAX_OUTPUT_TOKENS),
                            entry("a-smart-model", ChatModelRolesConfig.SMART_MAX_OUTPUT_TOKENS));
                });
    }

    @Test
    void shouldFailFastWhenTheProviderDeclaresNoModelToFallBackOn() {
        runner.withPropertyValues("llm.provider=unknown-provider")
                .run(ctx -> assertThat(ctx)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("llm.unknown-provider.model"));
    }

    @Configuration
    static class StubFactoryConfig {

        @Bean
        ChatModelFactory chatModelFactory() {
            return (modelName, maxOutputTokens) -> {
                requested.add(modelName);
                capsByModel.put(modelName, maxOutputTokens);
                return new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest request) {
                        throw new UnsupportedOperationException("stub");
                    }
                };
            };
        }
    }
}
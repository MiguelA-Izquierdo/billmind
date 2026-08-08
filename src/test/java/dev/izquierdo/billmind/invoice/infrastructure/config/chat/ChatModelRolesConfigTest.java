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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role beans are the single place where "which model serves this call" is decided, and the
 * fallback to the provider's model is an expression in application.properties — so the file on
 * the classpath is loaded here rather than restated, which is what puts the expression itself
 * under test instead of a copy of it that cannot drift.
 */
class ChatModelRolesConfigTest {

    /** Model names the factory was asked for, in bean-creation order. */
    private static final List<String> requested = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(ChatModelRolesConfig.class, StubFactoryConfig.class)
            .withInitializer(ctx -> {
                requested.clear();
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
        runner.withPropertyValues("llm.provider=groq", "llm.groq.model=llama-3.3-70b-versatile")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "llama-3.3-70b-versatile", "llama-3.3-70b-versatile");
                });
    }

    @Test
    void shouldGiveEachRoleItsOwnModelWhenBothOverridesAreSet() {
        runner.withPropertyValues(
                        "llm.provider=groq",
                        "llm.groq.model=llama-3.3-70b-versatile",
                        "llm.role.fast.model=llama-3.1-8b-instant",
                        "llm.role.smart.model=llama-3.3-70b-versatile")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "llama-3.1-8b-instant", "llama-3.3-70b-versatile");
                });
    }

    @Test
    void shouldOverrideOnlyTheRoleThatDeclaresItsOwnModel() {
        runner.withPropertyValues(
                        "llm.provider=groq",
                        "llm.groq.model=llama-3.3-70b-versatile",
                        "llm.role.fast.model=llama-3.1-8b-instant")
                .run(ctx -> {
                    ctx.getBean("fastChatModel", ChatModel.class);
                    ctx.getBean("smartChatModel", ChatModel.class);
                    assertThat(requested).containsExactlyInAnyOrder(
                            "llama-3.1-8b-instant", "llama-3.3-70b-versatile");
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
            return modelName -> {
                requested.add(modelName);
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
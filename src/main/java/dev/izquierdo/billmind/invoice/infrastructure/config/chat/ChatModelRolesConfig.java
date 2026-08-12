package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.izquierdo.billmind._shared.infrastructure.llm.LlmTelemetry;
import dev.izquierdo.billmind._shared.infrastructure.llm.TimedChatLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines two semantic roles for LLM usage, each built from the active provider's factory.
 *
 * Roles:
 *   fastChatModel  — low-latency tasks: classification, PII redaction.
 *   smartChatModel — quality-sensitive tasks: structured field extraction, RAG, agent reasoning.
 *
 * Each role is built with its own model name, llm.role.{fast,smart}.model, which defaults to the
 * active provider's llm.{provider}.model — so leaving both unset reproduces the previous
 * single-model behaviour exactly. That fallback lives in application.properties, next to the
 * provider models it points at. Routing the roles to different *providers* is a separate step
 * (see PLAN.md), since the provider beans are still selected by a single llm.provider.
 *
 * The output cap is decided here too, per role, and handed to the factory: the Anthropic
 * integration rejects a per-request maxOutputTokens, so it belongs to the model. It is a constant
 * rather than a property because what sets it is the longest answer the code asks for, which does
 * not change between environments — and it is per role for the same reason the model name is:
 * binding one cap to both collapses two very different workloads onto the larger one.
 *
 * Both beans are wrapped with TimedChatLanguageModel, which logs per-call latency, role,
 * provider, model, and operation context (set via MDC by each caller). The model tag is
 * resolved per role, so telemetry attributes cost to the model that actually served the call.
 */
@Configuration
public class ChatModelRolesConfig {

    @Value("${llm.provider}")
    private String provider;

    @Value("${llm.role.fast.model}")
    private String fastModel;

    @Value("${llm.role.smart.model}")
    private String smartModel;

    /**
     * fast answers with a company name or a list of PII spans; smart with an extraction JSON, a
     * chat reply, or a JSON repair. Both sized well above the answer itself because a reasoning
     * model bills its chain of thought as output: the same PII scan costs 67 output tokens on
     * Claude Haiku 4.5 and over 512 on gpt-oss-20b, where a cap sized on the answer alone cut the
     * JSON in half and the redaction silently fell back to regex-only.
     *
     * <p>If a model ever reports {@code tokensOut} sitting exactly on its cap, it was truncated —
     * raise the number here. But raising it is not free: OpenAI-compatible providers count the
     * declared cap against the per-minute token budget, so prompt + cap has to fit inside the
     * model's whole TPM limit or every single request is rejected 413, fresh minute or not. The
     * smart role's prompt is ~4.5K tokens against Groq's 8K free-tier limit, which is what caps
     * this at 2048 — the ceiling is the plan, and the way out of it is the plan, not this number.
     */
    static final int FAST_MAX_OUTPUT_TOKENS  = 2048;
    static final int SMART_MAX_OUTPUT_TOKENS = 2048;

    private final LlmTelemetry telemetry;

    public ChatModelRolesConfig(ObjectProvider<LlmTelemetry> telemetrySinks) {
        this.telemetry = LlmTelemetry.composite(telemetrySinks.orderedStream().toList());
    }

    @Bean("fastChatModel")
    public ChatModel fastChatModel(ChatModelFactory chatModelFactory) {
        return roleModel(chatModelFactory, "fast", fastModel, FAST_MAX_OUTPUT_TOKENS);
    }

    @Bean("smartChatModel")
    public ChatModel smartChatModel(ChatModelFactory chatModelFactory) {
        return roleModel(chatModelFactory, "smart", smartModel, SMART_MAX_OUTPUT_TOKENS);
    }

    private ChatModel roleModel(ChatModelFactory factory, String role, String model, int maxOutputTokens) {
        ChatModel delegate = factory.create(model, maxOutputTokens);
        return new TimedChatLanguageModel(delegate, role, provider, model, telemetry);
    }
}
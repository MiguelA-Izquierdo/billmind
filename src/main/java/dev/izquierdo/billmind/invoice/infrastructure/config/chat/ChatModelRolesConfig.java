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

    private final LlmTelemetry telemetry;

    public ChatModelRolesConfig(ObjectProvider<LlmTelemetry> telemetrySinks) {
        this.telemetry = LlmTelemetry.composite(telemetrySinks.orderedStream().toList());
    }

    @Bean("fastChatModel")
    public ChatModel fastChatModel(ChatModelFactory chatModelFactory) {
        return roleModel(chatModelFactory, "fast", fastModel);
    }

    @Bean("smartChatModel")
    public ChatModel smartChatModel(ChatModelFactory chatModelFactory) {
        return roleModel(chatModelFactory, "smart", smartModel);
    }

    private ChatModel roleModel(ChatModelFactory factory, String role, String model) {
        return new TimedChatLanguageModel(factory.create(model), role, provider, model, telemetry);
    }
}
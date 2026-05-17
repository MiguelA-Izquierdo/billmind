package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.izquierdo.billmind._shared.infrastructure.llm.TimedChatLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Defines two semantic roles for LLM usage, both backed by the same provider bean for now.
 *
 * Roles:
 *   fastChatModel  — low-latency tasks: classification, PII redaction.
 *                    Suitable for small/local models (e.g. Ollama llama3.2).
 *   smartChatModel — quality-sensitive tasks: structured field extraction, RAG, agent reasoning.
 *                    Suitable for capable cloud models (e.g. Claude Sonnet, GPT-4o).
 *
 * In dev both roles resolve to the same configured provider. To route them to different
 * models in production, replace these aliases with provider-specific beans using separate
 * llm.role.fast.* and llm.role.smart.* properties (see PLAN.md — Milestone 1 note).
 *
 * Both beans are wrapped with TimedChatLanguageModel, which logs per-call latency,
 * role, provider, model, and operation context (set via MDC by each caller).
 */
@Configuration
public class ChatModelRolesConfig {

    @Value("${llm.provider}")
    private String provider;

    private final Environment env;

    public ChatModelRolesConfig(Environment env) {
        this.env = env;
    }

    @Bean("fastChatModel")
    public ChatModel fastChatModel(ChatModel chatLanguageModel) {
        return new TimedChatLanguageModel(chatLanguageModel, "fast", provider, resolveModel());
    }

    @Bean("smartChatModel")
    public ChatModel smartChatModel(ChatModel chatLanguageModel) {
        return new TimedChatLanguageModel(chatLanguageModel, "smart", provider, resolveModel());
    }

    private String resolveModel() {
        return env.getProperty("llm." + provider + ".model", "unknown");
    }
}
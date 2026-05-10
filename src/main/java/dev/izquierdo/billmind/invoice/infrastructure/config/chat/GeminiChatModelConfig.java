package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Gemini exposes an OpenAI-compatible REST API — no separate SDK dependency required.
// Docs: https://ai.google.dev/gemini-api/docs/openai
@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini")
public class GeminiChatModelConfig {

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/";

    @Value("${llm.gemini.api-key}")
    private String apiKey;

    @Value("${llm.gemini.model:gemini-2.5-flash}")
    private String model;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(GEMINI_BASE_URL)
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
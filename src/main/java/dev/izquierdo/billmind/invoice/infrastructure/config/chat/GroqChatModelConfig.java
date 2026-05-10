package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "groq")
public class GroqChatModelConfig {

    @Value("${llm.groq.api-key}")
    private String apiKey;

    @Value("${llm.groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${llm.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
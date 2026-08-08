package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

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

    @Value("${llm.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Bean
    public ChatModelFactory chatModelFactory() {
        return modelName -> OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
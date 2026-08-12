package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiChatModelConfig {

    @Value("${llm.openai.api-key}")
    private String apiKey;

    @Bean
    public ChatModelFactory chatModelFactory() {
        return (modelName, maxOutputTokens) -> OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxOutputTokens)
                .build();
    }
}
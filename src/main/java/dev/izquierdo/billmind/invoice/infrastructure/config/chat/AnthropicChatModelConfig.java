package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class AnthropicChatModelConfig {

    @Value("${llm.anthropic.api-key}")
    private String apiKey;

    @Value("${llm.anthropic.model:claude-sonnet-4-6}")
    private String model;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
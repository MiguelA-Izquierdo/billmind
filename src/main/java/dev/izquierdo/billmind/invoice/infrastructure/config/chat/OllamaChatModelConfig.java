package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaChatModelConfig {

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${llm.ollama.model:llama3.2}")
    private String model;

    @Value("${llm.ollama.timeout-seconds:240}")
    private int timeoutSeconds;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
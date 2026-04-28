package com.demo.billmind.invoice.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    private static final String VECTOR_TABLE = "vector_store";
    private static final int VECTOR_DIMENSIONS = 384;
    private static final int OLLAMA_TIMEOUT_SECONDS = 240;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String chatModelName;

    @Value("${spring.datasource.host}")
    private String dbHost;

    @Value("${spring.datasource.port}")
    private int dbPort;

    @Value("${spring.datasource.database}")
    private String dbName;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public ChatLanguageModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(chatModelName)
                .timeout(Duration.ofSeconds(OLLAMA_TIMEOUT_SECONDS))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbName)
                .user(username)
                .password(password)
                .table(VECTOR_TABLE)
                .dimension(VECTOR_DIMENSIONS)
                .build();
    }
}

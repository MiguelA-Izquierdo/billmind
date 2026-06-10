package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

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

    @Value("${pgvector.table-name}")
    private String vectorTable;

    @Value("${pgvector.dimensions}")
    private int vectorDimensions;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // useIndex=false: index lifecycle is managed by JpaKnowledgeRepository.rebuildIndex().
        // indexListSize is required by the beta5 API even when useIndex=false.
        return PgVectorEmbeddingStore.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbName)
                .user(username)
                .password(password)
                .table(vectorTable)
                .dimension(vectorDimensions)
                .useIndex(false)
                .indexListSize(1)
                .build();
    }
}
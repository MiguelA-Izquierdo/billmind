package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
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

    // langchain4j-pgvector:1.0.0-beta5 only supports IVFFlat via useIndex(boolean).
    // HNSW is not available in this release; set to false to skip index creation entirely.
    @Value("${pgvector.use-index:true}")
    private boolean useIndex;

    @Value("${pgvector.index-list-size:100}")
    private int indexListSize;

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
                .table(vectorTable)
                .dimension(vectorDimensions)
                .useIndex(useIndex)
                .indexListSize(indexListSize)
                .build();
    }
}
package com.demo.billmind.invoice.infrastructure.adapter;

import com.demo.billmind.invoice.domain.model.InvoiceChunk;
import com.demo.billmind.invoice.domain.model.InvoiceReference;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Testcontainers
class PgVectorInvoiceRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("billmind_test")
            .withUsername("test")
            .withPassword("test");

    private PgVectorInvoiceRepository repository;

    @BeforeEach
    void setUp() {
        String jdbcUrl = postgres.getJdbcUrl();
        String host = postgres.getHost();
        int port = postgres.getMappedPort(5432);

        var embeddingStore = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database("billmind_test")
                .user("test")
                .password("test")
                .table("vector_store_test")
                .dimension(384)
                .createTable(true)
                .build();

        var embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        repository = new PgVectorInvoiceRepository(embeddingStore, embeddingModel);
    }

    @Test
    void store_withValidChunks_persistsWithoutError() {
        UUID invoiceId = UUID.randomUUID();
        List<InvoiceChunk> chunks = List.of(
                new InvoiceChunk("Importe total: 120,50 EUR", new InvoiceReference(invoiceId, 1, "Resumen")),
                new InvoiceChunk("Periodo de facturacion: enero 2025", new InvoiceReference(invoiceId, 1, "Cabecera"))
        );

        assertDoesNotThrow(() -> repository.store(chunks));
    }

    @Test
    void store_withEmptyList_doesNothing() {
        assertDoesNotThrow(() -> repository.store(List.of()));
    }
}

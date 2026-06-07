package dev.izquierdo.billmind;

import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionJpaRepository;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.infrastructure.persistence.InvoiceJpaRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeChunkJpaRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeDocumentJpaRepository;
import dev.izquierdo.billmind.market.infrastructure.persistence.ElectricityRateJpaRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
class BillMindApplicationTests {

    @MockitoBean
    EmbeddingStore<TextSegment> embeddingStore;

    @MockitoBean
    StartupReadinessChecker startupReadinessChecker;

    @MockitoBean
    InvoiceJpaRepository invoiceJpaRepository;

    @MockitoBean
    SessionJpaRepository sessionJpaRepository;

    @MockitoBean
    InvoiceFieldExtractor invoiceFieldExtractor;

    @MockitoBean
    ElectricityRateJpaRepository marketRateJpaRepository;

    @MockitoBean
    KnowledgeChunkJpaRepository knowledgeChunkJpaRepository;

    @MockitoBean
    KnowledgeDocumentJpaRepository knowledgeDocumentJpaRepository;

    @Test
    void contextLoads() {
    }

}

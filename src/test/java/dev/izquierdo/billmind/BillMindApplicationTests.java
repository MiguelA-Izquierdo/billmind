package dev.izquierdo.billmind;

import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind._shared.infrastructure.persistence.SessionJpaRepository;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.infrastructure.persistence.InvoiceJpaRepository;
import dev.izquierdo.billmind.market.infrastructure.persistence.ElectricityRateJpaRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
class BillMindApplicationTests {

    @MockBean
    EmbeddingStore<TextSegment> embeddingStore;

    @MockBean
    StartupReadinessChecker startupReadinessChecker;

    @MockBean
    InvoiceJpaRepository invoiceJpaRepository;

    @MockBean
    SessionJpaRepository sessionJpaRepository;

    @MockBean
    InvoiceFieldExtractor invoiceFieldExtractor;

    @MockBean
    ElectricityRateJpaRepository marketRateJpaRepository;

    @Test
    void contextLoads() {
    }

}

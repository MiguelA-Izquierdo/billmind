package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.market.application.command.SaveElectricityRateCommand;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Scope: verifies Kafka wiring only — that the consumer deserializes events, routes
// them to CommandBus, and sends domain errors to the correct topic. The command
// handler, use case, and persistence layer are mocked out.
// For full end-to-end coverage (consumer → DB), see SaveElectricityRateUseCaseTest + JpaElectricityRateRepositoryTest.
@SpringBootTest
@DirtiesContext
// Retry topics are named with SUFFIX_WITH_INDEX_VALUE strategy (see @RetryableTopic on the consumer):
//   market.electricity-price-updated-0  (retry attempt 1)
//   market.electricity-price-updated-1  (retry attempt 2)
// These must match the suffix strategy or EmbeddedKafka will fail to route retries.
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "market.electricity-price-updated",
        "market.electricity-price-updated-0",
        "market.electricity-price-updated-1",
        "market.electricity-price-updated.DLT",
        "market.electricity-price-updated.domain-errors"
    }
)
@TestPropertySource(properties = {
    "kafka.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class ElectricityPriceConsumerKafkaWiringIT {

    private static final String EVENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.host", postgres::getHost);
        registry.add("spring.datasource.port", () -> postgres.getMappedPort(5432).toString());
        registry.add("spring.datasource.database", postgres::getDatabaseName);
    }

    @MockitoBean private StartupReadinessChecker startupReadinessChecker;
    @MockitoBean private EmbeddingStore<TextSegment> embeddingStore;
    @MockitoBean private InvoiceFieldExtractor invoiceFieldExtractor;
    @MockitoBean private CommandBus commandBus;

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void shouldDispatchCommandWhenValidEventArrives() {
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            EVENT_ID, "IBERDROLA", "2.0TD",
            new BigDecimal("0.150000"), null, null, null, null, null,
            LocalDate.of(2025, 1, 1), null, "PENINSULA", "REE", null
        );

        kafkaTemplate.send("market.electricity-price-updated", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<SaveElectricityRateCommand> captor = ArgumentCaptor.forClass(SaveElectricityRateCommand.class);
            verify(commandBus).dispatch(captor.capture());
            SaveElectricityRateCommand command = captor.getValue();
            assertThat(command.id()).isEqualTo(UUID.fromString(EVENT_ID));
            assertThat(command.company()).isEqualTo("IBERDROLA");
            assertThat(command.tariffName()).isEqualTo("2.0TD");
            assertThat(command.pricePerKwh()).isEqualByComparingTo("0.150000");
        });
    }

    @Test
    void shouldSendToDomainErrorsWhenDomainValidationFails() {
        Consumer<String, String> testConsumer = createTestConsumer("test-domain-errors-" + UUID.randomUUID());
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, "market.electricity-price-updated.domain-errors");

        ElectricityPriceEvent invalidEvent = new ElectricityPriceEvent(
            EVENT_ID, "IBERDROLA", "2.0TD",
            new BigDecimal("-1.00"), null, null, null, null, null,
            LocalDate.of(2025, 1, 1), null, null, "REE", null
        );

        kafkaTemplate.send("market.electricity-price-updated", invalidEvent);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        verify(commandBus, never()).dispatch(any());
        testConsumer.close();
    }

    private Consumer<String, String> createTestConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
            .createConsumer();
    }
}
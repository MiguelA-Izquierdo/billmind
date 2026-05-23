package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.infrastructure.kafka.KafkaEvent;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.market.application.command.SaveElectricityRateCommand;
import dev.izquierdo.billmind.market.domain.exceptions.InvalidElectricityRateException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ElectricityPriceConsumerTest {

    private static final String DOMAIN_ERRORS_TOPIC = "market.electricity-price-updated.domain-errors";
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 1);
    private static final String EVENT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Mock private CommandBus commandBus;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private ElectricityPriceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ElectricityPriceConsumer(commandBus, kafkaTemplate, DOMAIN_ERRORS_TOPIC);
        lenient().when(kafkaTemplate.send(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldDispatchCommandWhenValidEvent() {
        Instant publishedAt = Instant.parse("2025-01-01T00:00:00Z");
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            EVENT_ID, "IBERDROLA", "2.0TD",
            new BigDecimal("0.150000"), null, null, null, null, null,
            TODAY, null, "PENINSULA", "REE", publishedAt
        );

        consumer.consume(record(event));

        ArgumentCaptor<SaveElectricityRateCommand> captor = ArgumentCaptor.forClass(SaveElectricityRateCommand.class);
        verify(commandBus).dispatch(captor.capture());
        SaveElectricityRateCommand command = captor.getValue();
        assertThat(command.id()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(command.supplyType()).isEqualTo(InvoiceType.LUZ);
        assertThat(command.company()).isEqualTo("IBERDROLA");
        assertThat(command.tariffName()).isEqualTo("2.0TD");
        assertThat(command.pricePerKwh()).isEqualByComparingTo("0.150000");
        assertThat(command.receivedAt()).isEqualTo(publishedAt);
    }

    @Test
    void shouldSendToDomainErrorsWhenValidFromIsNull() {
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            EVENT_ID, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            null, null, null, "REE", null
        );

        consumer.consume(record(event));

        verify(kafkaTemplate).send(eq(DOMAIN_ERRORS_TOPIC), any(ElectricityPriceDomainErrorEvent.class));
        verify(commandBus, never()).dispatch(any());
    }

    @Test
    void shouldSendToDomainErrorsWhenDispatchThrowsDomainValidationError() {
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            EVENT_ID, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        );
        doThrow(new InvalidElectricityRateException("pricePerKwh must be non-negative: -0.01"))
            .when(commandBus).dispatch(any());

        consumer.consume(record(event));

        verify(kafkaTemplate).send(eq(DOMAIN_ERRORS_TOPIC), any(ElectricityPriceDomainErrorEvent.class));
    }

    @Test
    void shouldThrowWhenEventIdIsNull() {
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            null, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        );

        assertThatThrownBy(() -> consumer.consume(record(event)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventId cannot be null");

        verify(commandBus, never()).dispatch(any());
    }

    @Test
    void shouldThrowWhenEventIdIsNotValidUuid() {
        ElectricityPriceEvent event = new ElectricityPriceEvent(
            "not-a-uuid", "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        );

        assertThatThrownBy(() -> consumer.consume(record(event)))
            .isInstanceOf(IllegalArgumentException.class);

        verify(commandBus, never()).dispatch(any());
    }

    @Test
    void shouldThrowWhenEventTypeIsNotElectricityPriceEvent() {
        KafkaEvent unknown = () -> "evt-unknown";
        ConsumerRecord<String, KafkaEvent> record = new ConsumerRecord<>(
            "market.electricity-price-updated", 0, 0L, null, unknown);

        assertThatThrownBy(() -> consumer.consume(record))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unexpected event type");

        verify(commandBus, never()).dispatch(any());
    }

    private ConsumerRecord<String, KafkaEvent> record(ElectricityPriceEvent event) {
        return new ConsumerRecord<>("market.electricity-price-updated", 0, 0L, null, event);
    }
}
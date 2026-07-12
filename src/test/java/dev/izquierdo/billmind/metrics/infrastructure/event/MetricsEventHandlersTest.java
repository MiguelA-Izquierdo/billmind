package dev.izquierdo.billmind.metrics.infrastructure.event;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.assistant.domain.event.AssistantQuestionAnswered;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceIngested;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceRejected;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the routing contract: {@code SpringDomainEventPublisher} dispatches by
 * {@code supportsEventType()}, so a mismatched class would silently break delivery.
 * Handlers are log-only placeholders — we only assert the mapping and that {@code handle}
 * tolerates a well-formed event without throwing.
 */
class MetricsEventHandlersTest {

    @Test
    void invoiceIngestedHandlerSupportsItsEventAndHandlesIt() {
        InvoiceIngestedMetricsHandler handler = new InvoiceIngestedMetricsHandler();
        InvoiceIngested event = InvoiceIngested.of(
                UUID.randomUUID(), UUID.randomUUID(), SupplyDomain.ELECTRICITY, "IBERDROLA", Instant.now());

        assertThat(handler.supportsEventType()).isEqualTo(InvoiceIngested.class);
        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void invoiceRejectedHandlerSupportsItsEventAndHandlesIt() {
        InvoiceRejectedMetricsHandler handler = new InvoiceRejectedMetricsHandler();
        InvoiceRejected event = InvoiceRejected.of(
                UUID.randomUUID(), UUID.randomUUID(), SupplyDomain.GAS,
                InvoiceRejected.Reason.UNSUPPORTED_SUPPLY_TYPE);

        assertThat(handler.supportsEventType()).isEqualTo(InvoiceRejected.class);
        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void assistantQuestionAnsweredHandlerSupportsItsEventAndHandlesIt() {
        AssistantQuestionAnsweredMetricsHandler handler = new AssistantQuestionAnsweredMetricsHandler();
        AssistantQuestionAnswered event = AssistantQuestionAnswered.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 42, 0);

        assertThat(handler.supportsEventType()).isEqualTo(AssistantQuestionAnswered.class);
        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
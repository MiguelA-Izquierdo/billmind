package dev.izquierdo.billmind.metrics.infrastructure.event;

import dev.izquierdo.billmind._shared.domain.event.DomainEventPublisher;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.infrastructure.event.SpringDomainEventPublisher;
import dev.izquierdo.billmind.assistant.domain.event.AssistantQuestionAnswered;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceIngested;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceRejected;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Scope: verifies the real domain-event bus wiring — that SpringDomainEventPublisher
// discovers the metrics handlers via constructor List injection and routes each event
// to ONLY its matching handler (dispatch by exact event class). Deliberately minimal:
// only the bus + the three handlers are loaded (no auto-configuration, so no DB / Kafka /
// LLM), keeping this fast and infra-free.
@SpringBootTest(classes = {
        SpringDomainEventPublisher.class,
        InvoiceIngestedMetricsHandler.class,
        InvoiceRejectedMetricsHandler.class,
        AssistantQuestionAnsweredMetricsHandler.class
})
class MetricsEventBusWiringIT {

    @Autowired
    private DomainEventPublisher publisher;

    @MockitoSpyBean private InvoiceIngestedMetricsHandler ingestedHandler;
    @MockitoSpyBean private InvoiceRejectedMetricsHandler rejectedHandler;
    @MockitoSpyBean private AssistantQuestionAnsweredMetricsHandler answeredHandler;

    @Test
    void routesInvoiceIngestedToOnlyItsHandler() {
        InvoiceIngested event = InvoiceIngested.of(
                UUID.randomUUID(), UUID.randomUUID(), SupplyDomain.ELECTRICITY, "IBERDROLA", Instant.now());

        publisher.publish(event);

        verify(ingestedHandler).handle(event);
        verify(rejectedHandler, never()).handle(any());
        verify(answeredHandler, never()).handle(any());
    }

    @Test
    void routesInvoiceRejectedToOnlyItsHandler() {
        InvoiceRejected event = InvoiceRejected.of(
                UUID.randomUUID(), UUID.randomUUID(), SupplyDomain.GAS,
                InvoiceRejected.Reason.UNSUPPORTED_SUPPLY_TYPE);

        publisher.publish(event);

        verify(rejectedHandler).handle(event);
        verify(ingestedHandler, never()).handle(any());
        verify(answeredHandler, never()).handle(any());
    }

    @Test
    void routesAssistantQuestionAnsweredToOnlyItsHandler() {
        AssistantQuestionAnswered event = AssistantQuestionAnswered.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 42, 0);

        publisher.publish(event);

        verify(answeredHandler).handle(event);
        verify(ingestedHandler, never()).handle(any());
        verify(rejectedHandler, never()).handle(any());
    }
}
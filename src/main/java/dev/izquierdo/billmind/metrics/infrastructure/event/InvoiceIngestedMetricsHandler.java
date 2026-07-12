package dev.izquierdo.billmind.metrics.infrastructure.event;

import dev.izquierdo.billmind._shared.domain.event.handle.DomainEventHandler;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceIngested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Metrics reaction to {@link InvoiceIngested}: the {@code metrics} bounded context
 * observes ingestion activity across other contexts without them knowing it exists.
 *
 * <p>Wiring is automatic: {@code SpringDomainEventPublisher} collects every
 * {@code DomainEventHandler} bean at startup and routes events by their exact class.
 *
 * <p>Placeholder implementation — for now it only logs. Once the metrics domain lands
 * (counters by supply type / provider, ingestion funnel), this adapter will translate
 * the event payload into a domain command/use case instead of logging.
 */
@Component
public class InvoiceIngestedMetricsHandler implements DomainEventHandler<InvoiceIngested> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceIngestedMetricsHandler.class);

    @Override
    public void handle(InvoiceIngested event) {
        InvoiceIngested.Payload payload = event.getData();
        log.info("[metrics] invoice ingested: supplyType={}, provider={}",
                payload.supplyType(), payload.provider());
    }

    @Override
    public Class<InvoiceIngested> supportsEventType() {
        return InvoiceIngested.class;
    }
}
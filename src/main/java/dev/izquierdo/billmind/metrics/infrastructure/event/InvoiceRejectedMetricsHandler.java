package dev.izquierdo.billmind.metrics.infrastructure.event;

import dev.izquierdo.billmind._shared.domain.event.handle.DomainEventHandler;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceRejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Metrics reaction to {@link InvoiceRejected}: feeds the upload-funnel drop-off view
 * (rejections by {@link InvoiceRejected.Reason} and detected supply type).
 *
 * <p>Placeholder implementation — logs only until the metrics domain lands.
 */
@Component
public class InvoiceRejectedMetricsHandler implements DomainEventHandler<InvoiceRejected> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRejectedMetricsHandler.class);

    @Override
    public void handle(InvoiceRejected event) {
        InvoiceRejected.Payload payload = event.getData();
        log.info("[metrics] invoice rejected: reason={}, detectedType={}",
                payload.reason(), payload.detectedType());
    }

    @Override
    public Class<InvoiceRejected> supportsEventType() {
        return InvoiceRejected.class;
    }
}
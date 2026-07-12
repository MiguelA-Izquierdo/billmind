package dev.izquierdo.billmind.invoice.domain.event;

import dev.izquierdo.billmind._shared.domain.event.BaseDomainEvent;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted once an uploaded invoice has been classified, PII-redacted, had its fields
 * extracted and been persisted — i.e. the ingestion pipeline completed successfully.
 *
 * <p>The payload carries only non-PII, cross-context-safe fields. It deliberately
 * excludes the raw (even redacted) invoice text so downstream handlers in other
 * bounded contexts never receive sensitive content — they can re-load the aggregate
 * by id if they need more.
 */
public final class InvoiceIngested extends BaseDomainEvent<InvoiceIngested.Payload> {

    public static final String EVENT_NAME = "invoice.ingested";

    public InvoiceIngested(Payload data) {
        super(data);
    }

    public static InvoiceIngested of(UUID invoiceId, UUID sessionId,
                                     SupplyDomain supplyType, String provider, Instant uploadedAt) {
        return new InvoiceIngested(new Payload(invoiceId, sessionId, supplyType, provider, uploadedAt));
    }

    @Override
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    public String getLogMessage() {
        Payload p = getData();
        return "Invoice ingested: id=" + p.invoiceId()
                + ", sessionId=" + p.sessionId()
                + ", supplyType=" + p.supplyType()
                + ", provider=" + p.provider();
    }

    public record Payload(
            UUID invoiceId,
            UUID sessionId,
            SupplyDomain supplyType,
            String provider,
            Instant uploadedAt
    ) {
    }
}
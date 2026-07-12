package dev.izquierdo.billmind.invoice.domain.event;

import dev.izquierdo.billmind._shared.domain.event.BaseDomainEvent;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.util.UUID;

/**
 * Emitted when the ingestion pipeline refuses an upload before persisting it — either the PDF
 * is not a utility supply invoice, or it is a supply type not yet supported in Phase 1.
 *
 * <p>Together with {@link InvoiceIngested} this closes the upload funnel: uploads →
 * (rejected, by {@link Reason}) → ingested. No persistence happens on this path, so there is
 * no transaction to consider — the event is published right before the rejection is thrown.
 */
public final class InvoiceRejected extends BaseDomainEvent<InvoiceRejected.Payload> {

    public static final String EVENT_NAME = "invoice.rejected";

    public InvoiceRejected(Payload data) {
        super(data);
    }

    public static InvoiceRejected of(UUID invoiceId, UUID sessionId,
                                     SupplyDomain detectedType, Reason reason) {
        return new InvoiceRejected(new Payload(invoiceId, sessionId, detectedType, reason));
    }

    @Override
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    public String getLogMessage() {
        Payload p = getData();
        return "Invoice rejected: id=" + p.invoiceId()
                + ", sessionId=" + p.sessionId()
                + ", detectedType=" + p.detectedType()
                + ", reason=" + p.reason();
    }

    /** Why the pipeline dropped the upload — the funnel drop-off reason. */
    public enum Reason {
        NOT_A_SUPPLY_INVOICE,
        UNSUPPORTED_SUPPLY_TYPE
    }

    public record Payload(
            UUID invoiceId,
            UUID sessionId,
            SupplyDomain detectedType,
            Reason reason
    ) {
    }
}
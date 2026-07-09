package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceContextPort {

    /**
     * Loads the invoice only when it belongs to the given session. An invoice owned by another
     * session yields {@link Optional#empty()}, so the assistant answers as if none was supplied.
     */
    Optional<Invoice> loadInvoice(UUID invoiceId, UUID sessionId);
}
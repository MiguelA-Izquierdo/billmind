package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceContextPort {
    Optional<Invoice> loadInvoice(UUID invoiceId);
}
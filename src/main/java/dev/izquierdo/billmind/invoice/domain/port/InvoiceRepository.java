package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    void save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findBySessionId(UUID sessionId);
}

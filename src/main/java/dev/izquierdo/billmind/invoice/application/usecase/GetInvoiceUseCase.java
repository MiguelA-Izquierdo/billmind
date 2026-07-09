package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceNotFoundException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;

    public GetInvoiceUseCase(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "InvoiceRepository cannot be null");
    }

    public Invoice execute(UUID invoiceId, UUID sessionId) {
        return findOwned(invoiceId, sessionId).orElseThrow(InvoiceNotFoundException::new);
    }

    /**
     * The single definition of "this invoice belongs to this session". An invoice owned by another
     * session is indistinguishable from a missing one, so callers cannot probe for existence.
     */
    public Optional<Invoice> findOwned(UUID invoiceId, UUID sessionId) {
        if (invoiceId == null || sessionId == null) {
            return Optional.empty();
        }
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> sessionId.equals(invoice.getSessionId()));
    }
}
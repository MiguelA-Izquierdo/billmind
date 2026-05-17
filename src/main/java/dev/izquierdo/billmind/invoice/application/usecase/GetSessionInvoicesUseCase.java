package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GetSessionInvoicesUseCase {

    private final InvoiceRepository invoiceRepository;

    public GetSessionInvoicesUseCase(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "InvoiceRepository cannot be null");
    }

    public List<Invoice> execute(UUID sessionId) {
        return invoiceRepository.findBySessionId(sessionId);
    }
}
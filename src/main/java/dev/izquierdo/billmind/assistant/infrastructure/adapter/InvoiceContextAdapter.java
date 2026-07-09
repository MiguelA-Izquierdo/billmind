package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.port.InvoiceContextPort;
import dev.izquierdo.billmind.invoice.application.usecase.GetInvoiceUseCase;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Bridges the assistant to the invoice bounded context. It goes through {@link GetInvoiceUseCase}
 * instead of the repository so ownership is enforced in exactly one place, shared with
 * {@code GET /api/v1/invoices/{id}}.
 */
@Component
public class InvoiceContextAdapter implements InvoiceContextPort {

    private final GetInvoiceUseCase getInvoiceUseCase;

    public InvoiceContextAdapter(GetInvoiceUseCase getInvoiceUseCase) {
        this.getInvoiceUseCase = getInvoiceUseCase;
    }

    @Override
    public Optional<Invoice> loadInvoice(UUID invoiceId, UUID sessionId) {
        return getInvoiceUseCase.findOwned(invoiceId, sessionId);
    }
}
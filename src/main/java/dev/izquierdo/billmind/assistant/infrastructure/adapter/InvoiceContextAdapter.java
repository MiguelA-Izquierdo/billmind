package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.port.InvoiceContextPort;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class InvoiceContextAdapter implements InvoiceContextPort {

    private final InvoiceRepository invoiceRepository;

    public InvoiceContextAdapter(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public Optional<String> loadRawText(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .map(invoice -> invoice.getRawTextRedacted());
    }
}
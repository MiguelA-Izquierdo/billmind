package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceChunkRepository;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class UploadInvoiceUseCase {
    private final InvoiceClassifier invoiceClassifier;
    private final InvoiceParser invoiceParser;
    private final InvoiceChunkRepository invoiceChunkRepository;

    public UploadInvoiceUseCase(InvoiceClassifier invoiceClassifier, InvoiceParser invoiceParser, InvoiceChunkRepository invoiceChunkRepository) {
        this.invoiceClassifier = Objects.requireNonNull(invoiceClassifier, "InvoiceClassifier cannot be null");
        this.invoiceParser = Objects.requireNonNull(invoiceParser, "InvoiceParser cannot be null");
        this.invoiceChunkRepository = Objects.requireNonNull(invoiceChunkRepository, "InvoiceChunkRepository cannot be null");
    }

    public void upload(Invoice invoice, byte[] pdfContent) {
        InvoiceClassification classification = invoiceClassifier.classify(pdfContent);
        if (!classification.isSupplyInvoice()) {
            throw new NotASupplyInvoiceException();
        }
        List<InvoiceChunk> chunks = invoiceParser.parseToChunks(invoice, pdfContent);
        invoiceChunkRepository.store(chunks);
    }
}

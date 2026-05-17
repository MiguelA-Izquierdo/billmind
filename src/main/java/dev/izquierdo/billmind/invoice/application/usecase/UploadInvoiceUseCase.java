package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import dev.izquierdo.billmind.invoice.domain.port.PiiRedactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UploadInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadInvoiceUseCase.class);

    private final InvoiceClassifier   invoiceClassifier;
    private final InvoiceParser       invoiceParser;
    private final PiiRedactor         piiRedactor;
    private final InvoiceFieldExtractor fieldExtractor;
    private final InvoiceRepository   invoiceRepository;

    public UploadInvoiceUseCase(
            InvoiceClassifier invoiceClassifier,
            InvoiceParser invoiceParser,
            PiiRedactor piiRedactor,
            InvoiceFieldExtractor fieldExtractor,
            InvoiceRepository invoiceRepository) {
        this.invoiceClassifier = Objects.requireNonNull(invoiceClassifier, "InvoiceClassifier cannot be null");
        this.invoiceParser     = Objects.requireNonNull(invoiceParser,     "InvoiceParser cannot be null");
        this.piiRedactor       = Objects.requireNonNull(piiRedactor,       "PiiRedactor cannot be null");
        this.fieldExtractor    = Objects.requireNonNull(fieldExtractor,    "InvoiceFieldExtractor cannot be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "InvoiceRepository cannot be null");
    }

    public void upload(Invoice invoice, byte[] pdfContent) {
        String rawText = invoiceParser.extractText(pdfContent);
        InvoiceClassification classification = invoiceClassifier.classify(rawText);
        if (!classification.isSupplyInvoice()) {
            throw new NotASupplyInvoiceException();
        }
        Invoice classified = invoice.withClassification(classification);

        String redactedText = piiRedactor.redact(rawText);
        InvoiceFields fields = fieldExtractor.extract(redactedText, classified.getSupplyType());


        Invoice ready = classified.withExtractedData(fields, redactedText);
        log.debug("Saving invoice: {}", ready);
        invoiceRepository.save(ready);
    }
}
package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.event.InvoiceIngested;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceRejected;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.exceptions.UnsupportedSupplyTypeException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.event.DomainEventPublisher;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
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
    private final DomainEventPublisher eventPublisher;

    public UploadInvoiceUseCase(
            InvoiceClassifier invoiceClassifier,
            InvoiceParser invoiceParser,
            PiiRedactor piiRedactor,
            InvoiceFieldExtractor fieldExtractor,
            InvoiceRepository invoiceRepository,
            DomainEventPublisher eventPublisher) {
        this.invoiceClassifier = Objects.requireNonNull(invoiceClassifier, "InvoiceClassifier cannot be null");
        this.invoiceParser     = Objects.requireNonNull(invoiceParser,     "InvoiceParser cannot be null");
        this.piiRedactor       = Objects.requireNonNull(piiRedactor,       "PiiRedactor cannot be null");
        this.fieldExtractor    = Objects.requireNonNull(fieldExtractor,    "InvoiceFieldExtractor cannot be null");
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository, "InvoiceRepository cannot be null");
        this.eventPublisher    = Objects.requireNonNull(eventPublisher,    "DomainEventPublisher cannot be null");
    }

    public void upload(Invoice invoice, byte[] pdfContent) {
        String rawText = invoiceParser.extractText(pdfContent);
        InvoiceClassification classification = invoiceClassifier.classify(rawText);
        if (!classification.isSupplyInvoice()) {
            publishRejected(invoice, classification, InvoiceRejected.Reason.NOT_A_SUPPLY_INVOICE);
            throw new NotASupplyInvoiceException();
        }
        if (classification.getType() != SupplyDomain.ELECTRICITY) {
            publishRejected(invoice, classification, InvoiceRejected.Reason.UNSUPPORTED_SUPPLY_TYPE);
            throw new UnsupportedSupplyTypeException(classification.getType());
        }
        Invoice classified = invoice.withClassification(classification);

        String redactedText = piiRedactor.redact(rawText);
        InvoiceFields fields = fieldExtractor.extract(redactedText, classified.getSupplyType());


        Invoice ready = classified.withExtractedData(fields, redactedText);
        log.debug("Saving invoice: {}", ready);
        invoiceRepository.save(ready);
        publishIngested(ready);
    }

    // Published after save() returns: the narrow Spring Data transaction has already
    // committed, so this is effectively after-commit. Do NOT wrap upload() in a wide
    // @Transactional (the LLM calls above would hold a DB connection for seconds).
    // See docs/PLAN.md ("Transactional outbox") for the durability upgrade path.
    private void publishIngested(Invoice invoice) {
        eventPublisher.publish(InvoiceIngested.of(
                invoice.getId(),
                invoice.getSessionId(),
                invoice.getSupplyType(),
                invoice.getProvider(),
                invoice.getUploadedAt()));
    }

    // No persistence happens on the rejection path, so there is no transaction to await.
    private void publishRejected(Invoice invoice, InvoiceClassification classification,
                                 InvoiceRejected.Reason reason) {
        eventPublisher.publish(InvoiceRejected.of(
                invoice.getId(),
                invoice.getSessionId(),
                classification.getType(),
                reason));
    }
}
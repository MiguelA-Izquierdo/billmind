package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.KeywordInvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.LlmInvoiceClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HybridInvoiceClassifier implements InvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(HybridInvoiceClassifier.class);

    private final KeywordInvoiceClassifier keywordClassifier;
    private final LlmInvoiceClassifier llmClassifier;

    public HybridInvoiceClassifier(KeywordInvoiceClassifier keywordClassifier,
                                   LlmInvoiceClassifier llmClassifier) {
        this.keywordClassifier = keywordClassifier;
        this.llmClassifier     = llmClassifier;
    }

    @Override
    public InvoiceClassification classify(String text) {
        if (text.isBlank()) {
            log.warn("Empty invoice text — classifying as OTRO");
            return new InvoiceClassification(InvoiceType.OTRO, "DESCONOCIDA");
        }

        Optional<InvoiceType> keywordMatch = keywordClassifier.classify(text);
        if (keywordMatch.isPresent()) {
            String company;
            try {
                company = llmClassifier.extractCompany(text);
            } catch (RuntimeException e) {
                throw new LlmServiceUnavailableException(e);
            }
            InvoiceClassification result = new InvoiceClassification(keywordMatch.get(), company);
            log.info("Keyword classification → type={}, company={}", result.getType(), result.getCompany());
            return result;
        }

        InvoiceClassification result;
        try {
            result = llmClassifier.classify(text);
        } catch (RuntimeException e) {
            throw new LlmServiceUnavailableException(e);
        }
        log.info("LLM classification → type={}, company={}", result.getType(), result.getCompany());
        return result;
    }
}
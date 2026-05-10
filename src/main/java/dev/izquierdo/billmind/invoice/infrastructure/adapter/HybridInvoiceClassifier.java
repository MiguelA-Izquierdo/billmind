package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.KeywordInvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.LlmInvoiceClassifier;
import dev.izquierdo.billmind._shared.infrastructure.pdf.PdfTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HybridInvoiceClassifier implements InvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(HybridInvoiceClassifier.class);

    private final PdfTextExtractor textExtractor;
    private final KeywordInvoiceClassifier keywordClassifier;
    private final LlmInvoiceClassifier llmClassifier;

    public HybridInvoiceClassifier(PdfTextExtractor textExtractor,
                                   KeywordInvoiceClassifier keywordClassifier,
                                   LlmInvoiceClassifier llmClassifier) {
        this.textExtractor     = textExtractor;
        this.keywordClassifier = keywordClassifier;
        this.llmClassifier     = llmClassifier;
    }

    @Override
    public InvoiceClassification classify(byte[] pdfContent) {
        String text = textExtractor.extract(pdfContent);

        if (text.isBlank()) {
            log.warn("No se pudo extraer texto del PDF — clasificando como OTRO");
            return new InvoiceClassification(InvoiceType.OTRO, "DESCONOCIDA");
        }

        Optional<InvoiceType> keywordMatch = keywordClassifier.classify(text);
        if (keywordMatch.isPresent()) {
            String company = llmClassifier.extractCompany(text);
            InvoiceClassification result = new InvoiceClassification(keywordMatch.get(), company);
            log.info("Clasificación por keywords → tipo={}, compania={}", result.getType(), result.getCompany());
            return result;
        }

        InvoiceClassification result = llmClassifier.classify(text);
        log.info("Clasificación por LLM → tipo={}, compania={}", result.getType(), result.getCompany());
        return result;
    }
}

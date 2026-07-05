package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.KeywordInvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.LlmInvoiceClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HybridInvoiceClassifier implements InvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(HybridInvoiceClassifier.class);

    private static final String CLASSIFY_TIMER = "invoice.classify.duration";

    private final KeywordInvoiceClassifier keywordClassifier;
    private final LlmInvoiceClassifier llmClassifier;
    private final MeterRegistry meterRegistry;

    public HybridInvoiceClassifier(KeywordInvoiceClassifier keywordClassifier,
                                   LlmInvoiceClassifier llmClassifier,
                                   MeterRegistry meterRegistry) {
        this.keywordClassifier = keywordClassifier;
        this.llmClassifier     = llmClassifier;
        this.meterRegistry     = meterRegistry;
    }

    @Override
    public InvoiceClassification classify(String text) {
        if (text.isBlank()) {
            log.warn("Empty invoice text — classifying as OTHER");
            return new InvoiceClassification(SupplyDomain.OTHER, "DESCONOCIDA");
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        Optional<SupplyDomain> keywordMatch = keywordClassifier.classify(text);
        if (keywordMatch.isPresent()) {
            InvoiceClassification result = new InvoiceClassification(keywordMatch.get(), extractCompany(text));
            sample.stop(classifyTimer("keyword"));
            log.debug("Keyword classification → type={}, company={}", result.getType(), result.getCompany());
            return result;
        }

        InvoiceClassification result = runLlmClassification(text);
        sample.stop(classifyTimer("llm"));
        log.debug("LLM classification → type={}, company={}", result.getType(), result.getCompany());
        return result;
    }

    private String extractCompany(String text) {
        try {
            return llmClassifier.extractCompany(text);
        } catch (RuntimeException e) {
            throw new LlmServiceUnavailableException(e);
        }
    }

    private InvoiceClassification runLlmClassification(String text) {
        try {
            return llmClassifier.classify(text);
        } catch (RuntimeException e) {
            throw new LlmServiceUnavailableException(e);
        }
    }

    private Timer classifyTimer(String strategy) {
        return Timer.builder(CLASSIFY_TIMER).tag("strategy", strategy).register(meterRegistry);
    }
}
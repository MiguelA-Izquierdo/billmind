package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.KeywordInvoiceClassifier;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier.LlmInvoiceClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridInvoiceClassifierTest {

    @Mock private KeywordInvoiceClassifier keywordClassifier;
    @Mock private LlmInvoiceClassifier llmClassifier;

    private MeterRegistry meterRegistry;
    private HybridInvoiceClassifier classifier;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        classifier = new HybridInvoiceClassifier(keywordClassifier, llmClassifier, meterRegistry);
    }

    // ── Texto vacío ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnOtroWhenTextIsBlank() {
        InvoiceClassification result = classifier.classify("   ");

        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
        assertThat(result.getCompany()).isEqualTo("DESCONOCIDA");
        verifyNoInteractions(keywordClassifier, llmClassifier);
    }

    // ── Ruta por keywords ─────────────────────────────────────────────────────

    @Test
    void shouldUseKeywordTypeAndLlmCompanyWhenKeywordsMatch() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.of(SupplyDomain.ELECTRICITY));
        when(llmClassifier.extractCompany(anyString())).thenReturn("IBERDROLA");

        InvoiceClassification result = classifier.classify("CUPS kwh potencia contratada electricidad");

        assertThat(result.getType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(result.getCompany()).isEqualTo("IBERDROLA");
        verify(llmClassifier, never()).classify(anyString());
    }

    @Test
    void shouldCallOnlyExtractCompanyWhenKeywordsMatch() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.of(SupplyDomain.GAS));
        when(llmClassifier.extractCompany(anyString())).thenReturn("NATURGY");

        classifier.classify("gas natural m³ peaje de gas");

        verify(llmClassifier).extractCompany(anyString());
        verify(llmClassifier, never()).classify(anyString());
    }

    // ── Ruta por LLM completo ─────────────────────────────────────────────────

    @Test
    void shouldDelegateToLlmClassifierWhenNoKeywordMatch() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.empty());
        when(llmClassifier.classify(anyString()))
            .thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "DESCONOCIDA"));

        InvoiceClassification result = classifier.classify("factura de arrendamiento enero 2025");

        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
        verify(llmClassifier).classify(anyString());
        verify(llmClassifier, never()).extractCompany(anyString());
    }

    @Test
    void shouldReturnLlmResultDirectlyWhenNoKeywords() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.empty());
        when(llmClassifier.classify(anyString()))
            .thenReturn(new InvoiceClassification(SupplyDomain.TELECOM, "MOVISTAR"));

        InvoiceClassification result = classifier.classify("texto ambiguo sin keywords claras");

        assertThat(result.getType()).isEqualTo(SupplyDomain.TELECOM);
        assertThat(result.getCompany()).isEqualTo("MOVISTAR");
    }

    // ── Métricas ──────────────────────────────────────────────────────────────

    @Test
    void shouldTimeKeywordStrategyWhenKeywordsMatch() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.of(SupplyDomain.ELECTRICITY));
        when(llmClassifier.extractCompany(anyString())).thenReturn("IBERDROLA");

        classifier.classify("CUPS kwh potencia contratada electricidad");

        assertThat(meterRegistry.timer("invoice.classify.duration", "strategy", "keyword").count()).isEqualTo(1L);
    }

    @Test
    void shouldTimeLlmStrategyWhenNoKeywordMatch() {
        when(keywordClassifier.classify(anyString())).thenReturn(Optional.empty());
        when(llmClassifier.classify(anyString()))
            .thenReturn(new InvoiceClassification(SupplyDomain.TELECOM, "MOVISTAR"));

        classifier.classify("texto ambiguo sin keywords claras");

        assertThat(meterRegistry.timer("invoice.classify.duration", "strategy", "llm").count()).isEqualTo(1L);
    }
}
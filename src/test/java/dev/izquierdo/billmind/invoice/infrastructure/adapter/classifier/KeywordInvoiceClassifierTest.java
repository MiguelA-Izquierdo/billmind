package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordInvoiceClassifierTest {

    private final KeywordInvoiceClassifier classifier = new KeywordInvoiceClassifier();

    // ── LUZ ──────────────────────────────────────────────────────────────────

    @Test
    void shouldClassifyLuzWithSufficientKeywords() {
        String text = "CUPS ES0031 Potencia contratada 3,3 kW energía eléctrica término de energía";
        assertThat(classifier.classify(text)).contains(InvoiceType.LUZ);
    }

    @Test
    void shouldClassifyLuzCaseInsensitive() {
        String text = "Consumo en KWh y peaje de acceso contratado";
        assertThat(classifier.classify(text)).contains(InvoiceType.LUZ);
    }

    // ── GAS ──────────────────────────────────────────────────────────────────

    @Test
    void shouldClassifyGas() {
        String text = "Consumo gas natural 234 m³ PCS peaje de gas aplicado";
        assertThat(classifier.classify(text)).contains(InvoiceType.GAS);
    }

    // ── AGUA ─────────────────────────────────────────────────────────────────

    @Test
    void shouldClassifyAgua() {
        String text = "Servicio de abastecimiento saneamiento y canon del agua cuota de servicio agua";
        assertThat(classifier.classify(text)).contains(InvoiceType.AGUA);
    }

    // ── TELCO ────────────────────────────────────────────────────────────────

    @Test
    void shouldClassifyTelco() {
        String text = "Tarifa plana internet fibra 600 MB datos nacionales llamadas ilimitadas";
        assertThat(classifier.classify(text)).contains(InvoiceType.TELCO);
    }

    // ── Sin clasificación ─────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyWhenScoreBelowThreshold() {
        // Solo una keyword de LUZ → score 1 < MIN_SCORE 2
        String text = "Factura con CUPS pero sin más contexto eléctrico";
        assertThat(classifier.classify(text)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForGenericText() {
        String text = "Arrendamiento de local comercial enero 2025 importe total 1200€";
        assertThat(classifier.classify(text)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankText() {
        assertThat(classifier.classify("   ")).isEmpty();
    }

    // ── Ambigüedad ────────────────────────────────────────────────────────────

    @Test
    void shouldReturnTypeWithHighestScore() {
        // Texto con keywords de LUZ (3) y GAS (1) → gana LUZ
        String text = "CUPS kwh potencia contratada electricidad gas natural";
        Optional<InvoiceType> result = classifier.classify(text);
        assertThat(result).contains(InvoiceType.LUZ);
    }
}
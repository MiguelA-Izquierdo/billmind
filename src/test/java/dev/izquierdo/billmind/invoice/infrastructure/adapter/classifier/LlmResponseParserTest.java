package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser(
            new ObjectMapper(),
            new LlmResponseJsonSanitizer()
    );

    // ── JSON limpio ───────────────────────────────────────────────────────────

    @Test
    void shouldParseValidJson() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"LUZ\",\"compania\":\"IBERDROLA\"}");
        assertThat(result.getType()).isEqualTo(InvoiceType.LUZ);
        assertThat(result.getCompany()).isEqualTo("IBERDROLA");
    }

    @Test
    void shouldParseLowercaseType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"gas\",\"compania\":\"NATURGY\"}");
        assertThat(result.getType()).isEqualTo(InvoiceType.GAS);
    }

    @Test
    void shouldParseMixedCaseType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"Agua\",\"compania\":\"AGUAS\"}");
        assertThat(result.getType()).isEqualTo(InvoiceType.AGUA);
    }

    // ── JSON con formato markdown ──────────────────────────────────────────────

    @Test
    void shouldParseJsonWrappedInMarkdownFence() {
        String response = "```json\n{\"tipo\":\"TELCO\",\"compania\":\"MOVISTAR\"}\n```";
        InvoiceClassification result = parser.parse(response);
        assertThat(result.getType()).isEqualTo(InvoiceType.TELCO);
        assertThat(result.getCompany()).isEqualTo("MOVISTAR");
    }

    @Test
    void shouldExtractJsonEmbeddedInExtraText() {
        String response = "Aquí está la clasificación: {\"tipo\":\"LUZ\",\"compania\":\"ENDESA\"} espero que ayude.";
        InvoiceClassification result = parser.parse(response);
        assertThat(result.getType()).isEqualTo(InvoiceType.LUZ);
        assertThat(result.getCompany()).isEqualTo("ENDESA");
    }

    // ── Tipo desconocido o ausente ────────────────────────────────────────────

    @Test
    void shouldReturnOtroForUnknownType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"SEGUROS\",\"compania\":\"MAPFRE\"}");
        assertThat(result.getType()).isEqualTo(InvoiceType.OTRO);
    }

    @Test
    void shouldReturnOtroWhenTypeFieldMissing() {
        InvoiceClassification result = parser.parse("{\"compania\":\"IBERDROLA\"}");
        assertThat(result.getType()).isEqualTo(InvoiceType.OTRO);
    }

    @Test
    void shouldReturnEmptyCompanyWhenFieldMissing() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"LUZ\"}");
        assertThat(result.getCompany()).isEmpty();
    }

    // ── Respuestas malformadas ────────────────────────────────────────────────

    @Test
    void shouldFallbackToOtroOnInvalidJson() {
        InvoiceClassification result = parser.parse("esto no es json en absoluto");
        assertThat(result.getType()).isEqualTo(InvoiceType.OTRO);
        assertThat(result.getCompany()).isEmpty();
    }

    @Test
    void shouldFallbackToOtroOnEmptyResponse() {
        InvoiceClassification result = parser.parse("");
        assertThat(result.getType()).isEqualTo(InvoiceType.OTRO);
    }

    @Test
    void shouldFallbackToOtroOnNullishResponse() {
        InvoiceClassification result = parser.parse("null");
        assertThat(result.getType()).isEqualTo(InvoiceType.OTRO);
    }
}
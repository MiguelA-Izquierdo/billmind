package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
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
        InvoiceClassification result = parser.parse("{\"tipo\":\"ELECTRICITY\",\"compania\":\"IBERDROLA\"}");
        assertThat(result.getType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(result.getCompany()).isEqualTo("IBERDROLA");
    }

    @Test
    void shouldParseLowercaseType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"gas\",\"compania\":\"NATURGY\"}");
        assertThat(result.getType()).isEqualTo(SupplyDomain.GAS);
    }

    @Test
    void shouldParseMixedCaseType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"Water\",\"compania\":\"AGUAS\"}");
        assertThat(result.getType()).isEqualTo(SupplyDomain.WATER);
    }

    // ── JSON con formato markdown ──────────────────────────────────────────────

    @Test
    void shouldParseJsonWrappedInMarkdownFence() {
        String response = "```json\n{\"tipo\":\"TELECOM\",\"compania\":\"MOVISTAR\"}\n```";
        InvoiceClassification result = parser.parse(response);
        assertThat(result.getType()).isEqualTo(SupplyDomain.TELECOM);
        assertThat(result.getCompany()).isEqualTo("MOVISTAR");
    }

    @Test
    void shouldExtractJsonEmbeddedInExtraText() {
        String response = "Aquí está la clasificación: {\"tipo\":\"ELECTRICITY\",\"compania\":\"ENDESA\"} espero que ayude.";
        InvoiceClassification result = parser.parse(response);
        assertThat(result.getType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(result.getCompany()).isEqualTo("ENDESA");
    }

    // ── Tipo desconocido o ausente ────────────────────────────────────────────

    @Test
    void shouldReturnOtroForUnknownType() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"SEGUROS\",\"compania\":\"MAPFRE\"}");
        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
    }

    @Test
    void shouldReturnOtroWhenTypeFieldMissing() {
        InvoiceClassification result = parser.parse("{\"compania\":\"IBERDROLA\"}");
        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
    }

    /** The company field feeds invoices.provider, a varchar(255); an explanation must not reach it. */
    @Test
    void shouldRejectACompanyFieldThatIsAnExplanation() {
        String verbose = "esta factura la emite IBERDROLA ".repeat(10);

        InvoiceClassification result =
                parser.parse("{\"tipo\":\"ELECTRICITY\",\"compania\":\"" + verbose + "\"}");

        assertThat(result.getCompany()).isEqualTo("DESCONOCIDA");
    }

    @Test
    void shouldReturnEmptyCompanyWhenFieldMissing() {
        InvoiceClassification result = parser.parse("{\"tipo\":\"ELECTRICITY\"}");
        assertThat(result.getCompany()).isEmpty();
    }

    // ── Respuestas malformadas ────────────────────────────────────────────────

    @Test
    void shouldFallbackToOtroOnInvalidJson() {
        InvoiceClassification result = parser.parse("esto no es json en absoluto");
        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
        assertThat(result.getCompany()).isEmpty();
    }

    @Test
    void shouldFallbackToOtroOnEmptyResponse() {
        InvoiceClassification result = parser.parse("");
        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
    }

    @Test
    void shouldFallbackToOtroOnNullishResponse() {
        InvoiceClassification result = parser.parse("null");
        assertThat(result.getType()).isEqualTo(SupplyDomain.OTHER);
    }
}
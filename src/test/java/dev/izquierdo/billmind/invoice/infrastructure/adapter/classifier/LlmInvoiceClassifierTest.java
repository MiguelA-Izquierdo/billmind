package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmInvoiceClassifierTest {

    @Mock private ChatModel chatModel;
    @Mock private LlmResponseParser responseParser;

    private LlmInvoiceClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new LlmInvoiceClassifier(chatModel, responseParser);
    }

    @Test
    void shouldReturnParsedClassificationFromLlmResponse() {
        InvoiceClassification expected = new InvoiceClassification(SupplyDomain.ELECTRICITY, "IBERDROLA");
        when(chatModel.chat(anyString())).thenReturn("{\"tipo\":\"LUZ\",\"compania\":\"IBERDROLA\"}");
        when(responseParser.parse(anyString())).thenReturn(expected);

        InvoiceClassification result = classifier.classify("IBERDROLA CUPS ES0031 factura electricidad");

        assertThat(result).isSameAs(expected);
        verify(responseParser).parse("{\"tipo\":\"LUZ\",\"compania\":\"IBERDROLA\"}");
    }

    @Test
    void shouldTruncateTextToHeaderCharsBeforeClassifying() {
        String longText = "A".repeat(300);
        when(chatModel.chat(anyString())).thenReturn("{}");
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "DESCONOCIDA"));

        classifier.classify(longText);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        // prompt contains truncated text (200 chars), not the full 300
        assertThat(promptCaptor.getValue()).doesNotContain("A".repeat(201));
    }

    @Test
    void shouldReturnTheCompanyInASingleCall() {
        when(chatModel.chat(anyString())).thenReturn("NATURGY");

        String result = classifier.extractCompany("NATURGY GAS consumo 234 m³");

        assertThat(result).isEqualTo("NATURGY");
        verify(chatModel, times(1)).chat(anyString());
    }

    /** An explanation instead of a name used to reach invoices.provider and overflow varchar(255). */
    @Test
    void shouldTreatAnOversizedAnswerAsUnidentified() {
        when(chatModel.chat(anyString()))
                .thenReturn("The company that issues this invoice is IBERDROLA, ".repeat(10));

        String result = classifier.extractCompany("x".repeat(500));

        assertThat(result).isEqualTo("DESCONOCIDA");
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void shouldNeverReturnACompanyLongerThanTheProviderColumnAllows() {
        when(chatModel.chat(anyString())).thenReturn("y".repeat(300));

        String result = classifier.extractCompany("x".repeat(750));

        assertThat(result).isEqualTo("DESCONOCIDA");
    }

    /**
     * The issuer is rarely in the first few hundred characters — invoice number and dates are.
     * A single wide window reaches it without paying a second sequential round-trip.
     */
    @Test
    void shouldSeeTheIssuerBeyondTheFirstFewHundredCharsWithoutASecondCall() {
        when(chatModel.chat(anyString())).thenReturn("ENDESA");
        String text = "DATOS DE LA FACTURA Nº factura: P26CON099887766 fecha 16/07/2026 ".repeat(5)
                + "Endesa Energía, S.A. Unipersonal.";

        String result = classifier.extractCompany(text);

        assertThat(text.indexOf("Endesa Energía")).isGreaterThan(250);
        assertThat(result).isEqualTo("ENDESA");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel, times(1)).chat(captor.capture());
        assertThat(captor.getValue()).contains("Endesa Energía");
    }

    @Test
    void shouldCapThePreviewAtTheWindowSize() {
        when(chatModel.chat(anyString())).thenReturn("DESCONOCIDA");

        classifier.extractCompany("z".repeat(900));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        assertThat(captor.getValue()).contains("z".repeat(750)).doesNotContain("z".repeat(751));
    }

    @Test
    void shouldReturnDesconocidaWhenTheModelCannotIdentifyTheIssuer() {
        when(chatModel.chat(anyString())).thenReturn("DESCONOCIDA");

        String result = classifier.extractCompany("short text");

        assertThat(result).isEqualTo("DESCONOCIDA");
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void shouldCollapseFragmentedTextBeforeClassifying() {
        when(chatModel.chat(anyString())).thenReturn("{}");
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "DESCONOCIDA"));

        // >50% single-char tokens → should be collapsed ("I B E R D R O L A" → "IBERDROLA")
        String fragmented = "I B E R D R O L A CUPS E S 0 0 3 1 factura";
        classifier.classify(fragmented);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("IBERDROLA");
        assertThat(promptCaptor.getValue()).doesNotContain("I B E R D R O L A");
    }

    @Test
    void shouldNotCollapseNormalText() {
        when(chatModel.chat(anyString())).thenReturn("{}");
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "DESCONOCIDA"));

        String normal = "IBERDROLA factura de electricidad período enero 2024 importe total";
        classifier.classify(normal);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("IBERDROLA factura de electricidad");
    }
}
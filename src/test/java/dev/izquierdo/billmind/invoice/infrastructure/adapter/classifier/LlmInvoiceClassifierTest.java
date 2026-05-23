package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
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
        InvoiceClassification expected = new InvoiceClassification(InvoiceType.LUZ, "IBERDROLA");
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
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(InvoiceType.OTRO, "DESCONOCIDA"));

        classifier.classify(longText);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        // prompt contains truncated text (200 chars), not the full 300
        assertThat(promptCaptor.getValue()).doesNotContain("A".repeat(201));
    }

    @Test
    void shouldReturnCompanyOnFirstAttempt() {
        when(chatModel.chat(anyString())).thenReturn("NATURGY");

        String result = classifier.extractCompany("NATURGY GAS consumo 234 m³");

        assertThat(result).isEqualTo("NATURGY");
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void shouldRetryAndReturnCompanyOnSecondAttempt() {
        when(chatModel.chat(anyString()))
                .thenReturn("DESCONOCIDA")
                .thenReturn("ENDESA");

        // Need text long enough for 2 windows (each 250 chars)
        String text = "x".repeat(500);

        String result = classifier.extractCompany(text);

        assertThat(result).isEqualTo("ENDESA");
        verify(chatModel, times(2)).chat(anyString());
    }

    @Test
    void shouldReturnDesconocidaAfterAllAttemptsExhausted() {
        when(chatModel.chat(anyString())).thenReturn("DESCONOCIDA");

        String text = "x".repeat(750);

        String result = classifier.extractCompany(text);

        assertThat(result).isEqualTo("DESCONOCIDA");
        verify(chatModel, times(3)).chat(anyString());
    }

    @Test
    void shouldStopExtractingWhenTextIsShorterThanOneWindow() {
        when(chatModel.chat(anyString())).thenReturn("DESCONOCIDA");

        String result = classifier.extractCompany("short text");

        assertThat(result).isEqualTo("DESCONOCIDA");
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void shouldCollapseFragmentedTextBeforeClassifying() {
        when(chatModel.chat(anyString())).thenReturn("{}");
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(InvoiceType.OTRO, "DESCONOCIDA"));

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
        when(responseParser.parse(anyString())).thenReturn(new InvoiceClassification(InvoiceType.OTRO, "DESCONOCIDA"));

        String normal = "IBERDROLA factura de electricidad período enero 2024 importe total";
        classifier.classify(normal);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("IBERDROLA factura de electricidad");
    }
}
package dev.izquierdo.billmind.invoice.infrastructure.adapter.pii;

import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridPiiRedactorTest {

    @Mock
    private ChatModel chatModel;

    private MeterRegistry meterRegistry;
    private HybridPiiRedactor redactor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redactor = new HybridPiiRedactor(chatModel, meterRegistry);
    }

    // ── Regex ─────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactIbanAndTriggerLlmScan() {
        when(chatModel.chat(anyString())).thenReturn("Referencia de pago: [IBAN]");

        String result = redactor.redact("Referencia de pago: ES9121000418450200051332");

        assertThat(result).contains("[IBAN]").doesNotContain("ES91");
        verify(chatModel).chat(anyString());
    }

    @Test
    void shouldNotAlterTextWithNoPii() {
        String content = "Potencia contratada: 3,3 kW. Consumo: 245 kWh.";

        assertThat(redactor.redact(content)).isEqualTo(content);
        verifyNoInteractions(chatModel);
    }

    @Test
    void shouldRedactDniAndTriggerLlmScan() {
        when(chatModel.chat(anyString())).thenReturn("Identificación fiscal: [DNI]");

        String result = redactor.redact("Identificación fiscal: 12345678Z");

        assertThat(result).contains("[DNI]").doesNotContain("12345678Z");
        verify(chatModel).chat(anyString());
    }

    // ── Activación del LLM ────────────────────────────────────────────────────

    @Test
    void shouldCallLlmWhenPiiSignalPresent() {
        when(chatModel.chat(anyString())).thenReturn("Titular: [NOMBRE], [DIRECCIÓN]");

        String result = redactor.redact("Titular: Juan García López, Calle Mayor 3");

        assertThat(result).isEqualTo("Titular: [NOMBRE], [DIRECCIÓN]");
        verify(chatModel).chat(anyString());
    }

    @Test
    void shouldNotCallLlmWithoutPiiSignal() {
        redactor.redact("Consumo facturado: 245 kWh periodo enero 2025");

        verifyNoInteractions(chatModel);
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    @Test
    void shouldFallbackToRegexResultWhenLlmFails() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        String result = redactor.redact("Titular: Juan García, IBAN ES9121000418450200051332");

        assertThat(result).contains("[IBAN]").doesNotContain("ES91");
    }

    @Test
    void shouldFallbackToRegexResultWhenLlmReturnsBlank() {
        when(chatModel.chat(anyString())).thenReturn("   ");

        String content = "Titular: Juan García";
        assertThat(redactor.redact(content)).isEqualTo(content);
    }

    // ── Invariantes ───────────────────────────────────────────────────────────

    @Test
    void shouldApplyRegexAndLlmTogether() {
        // Regex redacts IBAN first; LLM receives the already-redacted text and handles name/address
        when(chatModel.chat(anyString())).thenReturn("Titular: [NOMBRE], [IBAN], [DIRECCIÓN]");

        String result = redactor.redact("Titular: Juan García, IBAN ES9121000418450200051332, Calle Mayor 3");

        assertThat(result).contains("[NOMBRE]", "[DIRECCIÓN]", "[IBAN]");
    }

    // ── Métricas ──────────────────────────────────────────────────────────────

    @Test
    void shouldCountInvocationWhenLlmScanTriggered() {
        when(chatModel.chat(anyString())).thenReturn("Titular: [NOMBRE]");

        redactor.redact("Titular: Juan García López");

        assertThat(meterRegistry.counter("pii.llm.invocations").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountFallbackWithInvalidResponseReasonWhenLlmRejected() {
        when(chatModel.chat(anyString())).thenReturn("   ");

        redactor.redact("Titular: Juan García");

        assertThat(meterRegistry.counter("pii.llm.fallbacks", "reason", "invalid_response").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountFailureAndFallbackWithExceptionReasonWhenLlmThrows() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        redactor.redact("Titular: Juan García");

        assertThat(meterRegistry.counter("pii.llm.failures", "reason", "RuntimeException").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("pii.llm.fallbacks", "reason", "exception").count()).isEqualTo(1.0);
    }
}
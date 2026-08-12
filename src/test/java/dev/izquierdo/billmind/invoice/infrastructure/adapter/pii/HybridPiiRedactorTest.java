package dev.izquierdo.billmind.invoice.infrastructure.adapter.pii;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmRateLimitedException;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridPiiRedactorTest {

    private static final String NO_PII = "{\"names\":[],\"addresses\":[]}";

    @Mock
    private ChatModel chatModel;

    private MeterRegistry meterRegistry;
    private HybridPiiRedactor redactor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // ObjectMapper and the sanitizer are pure and deterministic — mocking them would assert nothing.
        redactor = new HybridPiiRedactor(
                chatModel, new ObjectMapper(), new LlmResponseJsonSanitizer(), meterRegistry);
    }

    // ── Regex ─────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactIbanAndTriggerLlmScan() {
        when(chatModel.chat(anyString())).thenReturn(NO_PII);

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
        when(chatModel.chat(anyString())).thenReturn(NO_PII);

        String result = redactor.redact("Identificación fiscal: 12345678Z");

        assertThat(result).contains("[DNI]").doesNotContain("12345678Z");
        verify(chatModel).chat(anyString());
    }

    // ── Activación del LLM ────────────────────────────────────────────────────

    @Test
    void shouldReplaceTheSpansTheModelReports() {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"names\":[\"Juan García López\"],\"addresses\":[\"Calle Mayor 3\"]}");

        String result = redactor.redact("Titular: Juan García López, Calle Mayor 3");

        assertThat(result).isEqualTo("Titular: [NOMBRE], [DIRECCIÓN]");
        verify(chatModel).chat(anyString());
    }

    @Test
    void shouldNotCallLlmWithoutPiiSignal() {
        redactor.redact("Consumo facturado: 245 kWh periodo enero 2025");

        verifyNoInteractions(chatModel);
    }

    /**
     * The model only reads the first 2000 characters, but the contract-holder block repeats the
     * name further down. Rewriting the header alone used to leave that copy in the persisted text.
     */
    @Test
    void shouldRemoveReportedSpansBeyondTheScannedHeader() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"HOMER JAY SIMPSON\"],\"addresses\":[]}");
        String text = "Titular: HOMER JAY SIMPSON\n"
                + "Consumo facturado de 245 kWh en el periodo de enero.\n".repeat(50)
                + "Titular del contrato: HOMER JAY SIMPSON";

        String result = redactor.redact(text);

        assertThat(text.length()).isGreaterThan(2000);
        assertThat(result).doesNotContain("HOMER JAY SIMPSON");
        assertThat(result).contains("Titular: [NOMBRE]", "Titular del contrato: [NOMBRE]");
    }

    /**
     * A postal address occupies three lines of OCR and the model reports it as one. Matching it
     * exactly skipped it silently — for a redactor that means personal data left in the document.
     */
    @Test
    void shouldRemoveAnAddressTheModelFoldedOntoASingleLine() {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"names\":[],\"addresses\":[\"742 EVERGREEN TERRACE 97501 SPRINGFIELD\"]}");
        String text = "Dirección de suministro:\n742 EVERGREEN TERRACE\n97501 SPRINGFIELD\nESTADOS UNIDOS";

        String result = redactor.redact(text);

        assertThat(result).doesNotContain("EVERGREEN").contains("[DIRECCIÓN]");
    }

    /**
     * The header prints the name ALL CAPS and the contract block prints it Title Case; the model
     * reports whichever it read. Matching case-sensitively left "Cliente: Sara Gaston Pilo" in a
     * persisted invoice after redacting "SARA GASTON PILO" two pages above.
     */
    @Test
    void shouldRemoveTheSameNameWrittenInADifferentCase() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"SARA GASTON PILO\"],\"addresses\":[]}");
        String text = "Titular: SARA GASTON PILO\nCliente: Sara Gaston Pilo";

        String result = redactor.redact(text);

        assertThat(result).doesNotContain("Gaston").isEqualTo("Titular: [NOMBRE]\nCliente: [NOMBRE]");
    }

    // ── Spans que no se pueden aplicar ────────────────────────────────────────

    /** A span the model did not copy verbatim simply fails to match — it cannot corrupt the text. */
    @Test
    void shouldLeaveTextIntactWhenAReportedSpanIsNotInIt() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"PEDRO MARTÍNEZ\"],\"addresses\":[]}");
        String content = "Titular: Juan García López";

        assertThat(redactor.redact(content)).isEqualTo(content);
    }

    @Test
    void shouldIgnoreSpansTooShortToIdentifyAnyone() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"de\"],\"addresses\":[\"C/\"]}");
        String content = "Titular: Juan de la Calle C/ Mayor";

        assertThat(redactor.redact(content)).isEqualTo(content);
    }


    /**
     * A throttle is not a degraded answer — it is no answer. Continuing would persist the
     * customer's name and address in full while the caller sees a 200.
     */
    @Test
    void shouldFailTheUploadWhenTheProviderThrottles() {
        when(chatModel.chat(anyString()))
                .thenThrow(new LlmRateLimitedException(new RuntimeException("429"), Duration.ofSeconds(30)));

        assertThatThrownBy(() -> redactor.redact("Titular: Juan García López"))
                .isInstanceOf(LlmRateLimitedException.class);
    }

    @Test
    void shouldFailTheUploadWhenTheProviderIsUnavailable() {
        when(chatModel.chat(anyString()))
                .thenThrow(new LlmServiceUnavailableException(new RuntimeException("503")));

        assertThatThrownBy(() -> redactor.redact("Titular: Juan García López"))
                .isInstanceOf(LlmServiceUnavailableException.class);
    }

    // ── Fallbacks: hubo respuesta, pero no sirve ──────────────────────────────

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

    @Test
    void shouldFallbackToRegexResultWhenLlmAnswersWithProse() {
        when(chatModel.chat(anyString())).thenReturn("Here is the redacted text: Titular: [NOMBRE]");

        String content = "Titular: Juan García";
        assertThat(redactor.redact(content)).isEqualTo(content);
    }

    // ── Invariantes ───────────────────────────────────────────────────────────

    @Test
    void shouldApplyRegexAndLlmTogether() {
        // Regex redacts the IBAN first; the LLM sees the already-redacted text and reports name/address.
        when(chatModel.chat(anyString()))
                .thenReturn("{\"names\":[\"Juan García\"],\"addresses\":[\"Calle Mayor 3\"]}");

        String result = redactor.redact("Titular: Juan García, IBAN ES9121000418450200051332, Calle Mayor 3");

        assertThat(result).contains("[NOMBRE]", "[DIRECCIÓN]", "[IBAN]");
    }

    // ── Métricas ──────────────────────────────────────────────────────────────

    @Test
    void shouldCountInvocationWhenLlmScanTriggered() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"Juan García López\"],\"addresses\":[]}");

        redactor.redact("Titular: Juan García López");

        assertThat(meterRegistry.counter("pii.llm.invocations").count()).isEqualTo(1.0);
    }

    /** Data reported but left in place must be visible: a silent no-op is the worst outcome here. */
    @Test
    void shouldCountSpansTheModelReportedButDidNotCopyVerbatim() {
        when(chatModel.chat(anyString())).thenReturn("{\"names\":[\"PEDRO MARTÍNEZ\"],\"addresses\":[]}");

        redactor.redact("Titular: Juan García López");

        assertThat(meterRegistry.counter("pii.llm.spans.unmatched", "token", "[NOMBRE]").count()).isEqualTo(1.0);
    }

    /**
     * The model reports the name once per casing it read. Case-insensitive matching means the first
     * span already removed both, so counting the second as unredacted raised an alarm on a run that
     * had gone perfectly — and an alarm that cries wolf is the one nobody reads.
     */
    @Test
    void shouldNotCountADuplicateSpanReportedInAnotherCaseAsUnmatched() {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"names\":[\"SARA GASTON PILO\",\"Sara Gaston Pilo\"],\"addresses\":[]}");

        String result = redactor.redact("Titular: SARA GASTON PILO\nCliente: Sara Gaston Pilo");

        assertThat(result).isEqualTo("Titular: [NOMBRE]\nCliente: [NOMBRE]");
        assertThat(meterRegistry.counter("pii.llm.spans.unmatched", "token", "[NOMBRE]").count()).isZero();
    }

    /** Deduplication folds whitespace too — the same address wrapped differently is one span. */
    @Test
    void shouldNotCountADuplicateSpanReportedWithDifferentSpacingAsUnmatched() {
        when(chatModel.chat(anyString()))
                .thenReturn("{\"names\":[],\"addresses\":[\"Calle Mayor 3\",\"Calle  Mayor   3\"]}");

        redactor.redact("Titular: Juan García, Calle Mayor 3");

        assertThat(meterRegistry.counter("pii.llm.spans.unmatched", "token", "[DIRECCIÓN]").count()).isZero();
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

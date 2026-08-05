package dev.izquierdo.billmind._shared.infrastructure;

import dev.izquierdo.billmind._shared.domain.exceptions.LlmRateLimitedException;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldAnswer429WithRetryAfterWhenTheProviderNamedTheWait() {
        LlmRateLimitedException throttled =
                new LlmRateLimitedException(new RuntimeException("429"), Duration.ofSeconds(90));

        ResponseEntity<ErrorResponseDTO> response = handler.handleLlmRateLimitedException(throttled);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("90");
        assertThat(response.getBody().message()).contains("Vuelve a intentarlo en 2 minutos.");
    }

    /** An exhausted daily quota lasts hours; the header stays in seconds and the copy switches unit. */
    @Test
    void shouldAnswer429InSecondsWhenTheWaitLastsHours() {
        LlmRateLimitedException throttled = new LlmRateLimitedException(
                new RuntimeException("429"), Duration.ofHours(11).plusMinutes(2).plusSeconds(31));

        ResponseEntity<ErrorResponseDTO> response = handler.handleLlmRateLimitedException(throttled);

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("39751");
        assertThat(response.getBody().message()).contains("Vuelve a intentarlo en 12 horas.");
    }

    @Test
    void shouldOmitRetryAfterWhenTheProviderNamedNoWait() {
        LlmRateLimitedException throttled =
                new LlmRateLimitedException(new RuntimeException("429"), Duration.ZERO);

        ResponseEntity<ErrorResponseDTO> response = handler.handleLlmRateLimitedException(throttled);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
        assertThat(response.getBody().message())
                .startsWith(throttled.getMessage())
                .endsWith("Inténtalo de nuevo más tarde.");
    }

    @Test
    void shouldKeepThrottlingSeparateFromOutage() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleLlmServiceUnavailableException(new LlmServiceUnavailableException(new RuntimeException()));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void shouldNeverLeakTheProviderFailureIntoTheBody() {
        RuntimeException providerFailure = new RuntimeException("{\"error\":\"invalid api key sk-live-1234\"}");

        ResponseEntity<ErrorResponseDTO> response = handler.handleAllExceptions(providerFailure);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message())
                .isEqualTo("Se ha producido un error interno en el servidor")
                .doesNotContain("sk-live");
    }
}
package dev.izquierdo.billmind._shared.domain.exceptions;

/**
 * The model could not be reached or failed on its side (5xx, timeout, DNS, bad credentials).
 * Distinct from {@link LlmRateLimitedException}: the caller did nothing to provoke it and waiting a
 * known amount of time is not the remedy.
 *
 * <p>The cause carries the provider's raw failure for the logs; it never reaches the client, since
 * every handler builds the response body from {@link #getMessage()}.
 */
public class LlmServiceUnavailableException extends RuntimeException {

    public LlmServiceUnavailableException(Throwable cause) {
        super("El servicio de procesamiento inteligente no está disponible temporalmente. Por favor, inténtalo de nuevo en unos minutos.", cause);
    }
}
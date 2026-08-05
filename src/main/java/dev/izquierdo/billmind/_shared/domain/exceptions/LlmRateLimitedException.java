package dev.izquierdo.billmind._shared.domain.exceptions;

import java.time.Duration;

/**
 * The model provider throttled us (HTTP 429 / quota exhausted). Kept apart from
 * {@link LlmServiceUnavailableException} because both the remedy and the UX differ: the caller must
 * wait, and {@link #retryAfter()} says how long whenever the provider told us.
 *
 * <p>{@code retryAfter} is {@link Duration#ZERO} when the provider gave no hint — callers must read
 * that as "unknown", never as "retry now".
 */
public class LlmRateLimitedException extends RuntimeException {

    /** Deliberately carries no "try again" advice: {@code ThrottleMessages} appends the wait. */
    private static final String MESSAGE = "Estamos atendiendo muchas consultas ahora mismo.";

    private final Duration retryAfter;

    public LlmRateLimitedException(Throwable cause, Duration retryAfter) {
        super(MESSAGE, cause);
        this.retryAfter = retryAfter != null && !retryAfter.isNegative() ? retryAfter : Duration.ZERO;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public boolean hasRetryAfter() {
        return !retryAfter.isZero();
    }
}
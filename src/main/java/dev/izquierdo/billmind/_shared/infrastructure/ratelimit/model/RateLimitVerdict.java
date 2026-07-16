package dev.izquierdo.billmind._shared.infrastructure.ratelimit.model;

import java.time.Duration;

/**
 * The aggregate outcome of checking every applicable layer of a profile at one phase. Distinguishes
 * an actual limit breach ({@link Status#THROTTLED} → HTTP 429) from a backing-store failure under a
 * fail-closed policy ({@link Status#UNAVAILABLE} → HTTP 503): the two must not be conflated.
 *
 * <p>{@code retryAfter} is when this request may be retried ({@code Retry-After}); {@code resetAfter}
 * is when the bucket is back at capacity ({@code X-RateLimit-Reset}). A denied caller can usually
 * retry long before the latter.
 *
 * <p>{@link #unlimited()} is the identity used while folding layers and when no layer applied (e.g.
 * a session-keyed profile on a request without a session header); it carries no header values.
 */
public record RateLimitVerdict(
        Status status,
        long limit,
        long remaining,
        Duration retryAfter,
        Duration resetAfter) {

    public enum Status { ALLOWED, THROTTLED, UNAVAILABLE }

    private static final RateLimitVerdict UNLIMITED =
            new RateLimitVerdict(Status.ALLOWED, -1, -1, Duration.ZERO, Duration.ZERO);

    public static RateLimitVerdict unlimited() {
        return UNLIMITED;
    }

    public static RateLimitVerdict allowed(long limit, long remaining, Duration resetAfter) {
        return new RateLimitVerdict(Status.ALLOWED, limit, remaining, Duration.ZERO, resetAfter);
    }

    public static RateLimitVerdict throttled(long limit, Duration retryAfter, Duration resetAfter) {
        return new RateLimitVerdict(Status.THROTTLED, limit, 0, retryAfter, resetAfter);
    }

    public static RateLimitVerdict unavailable() {
        return new RateLimitVerdict(Status.UNAVAILABLE, -1, -1, Duration.ZERO, Duration.ZERO);
    }

    public boolean rejected() {
        return status != Status.ALLOWED;
    }

    /** Whether {@code X-RateLimit-*} headers carry meaningful values (a layer was actually consulted). */
    public boolean hasHeaders() {
        return limit >= 0;
    }

    public int httpStatus() {
        return status == Status.THROTTLED ? 429 : 503;
    }
}
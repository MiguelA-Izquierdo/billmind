package dev.izquierdo.billmind._shared.infrastructure.ratelimit.model;

import java.time.Duration;

/**
 * The outcome of consuming from a bucket: whether the request is allowed, how many tokens remain,
 * the bucket's capacity (the {@code X-RateLimit-Limit} value), and — when denied — how long the
 * caller should wait before retrying ({@code Retry-After}).
 */
public record RateLimitDecision(boolean allowed, long remainingTokens, long limit, Duration retryAfter) {

    public static RateLimitDecision allowed(long remainingTokens, long limit) {
        return new RateLimitDecision(true, remainingTokens, limit, Duration.ZERO);
    }

    public static RateLimitDecision denied(long limit, Duration retryAfter) {
        return new RateLimitDecision(false, 0, limit, retryAfter);
    }
}
package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

import java.time.Duration;

/**
 * The resolved, numeric limit applied to one request: a token bucket of {@code capacity} tokens
 * refilled at {@code refillTokens} per {@code refillPeriod}, where each request draws {@code cost}
 * tokens. The design-level {@link FailMode} is inherited from the {@link RateLimitProfile}; the
 * numbers come from configuration.
 */
public record RateLimitPolicy(
        RateLimitProfile profile,
        long capacity,
        long refillTokens,
        Duration refillPeriod,
        long cost) {

    public RateLimitPolicy {
        if (capacity <= 0 || refillTokens <= 0 || cost <= 0) {
            throw new IllegalArgumentException("capacity, refillTokens and cost must be positive");
        }
        if (cost > capacity) {
            throw new IllegalArgumentException("cost cannot exceed capacity: a request would never pass");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
    }

    public FailMode failMode() {
        return profile.failMode();
    }
}
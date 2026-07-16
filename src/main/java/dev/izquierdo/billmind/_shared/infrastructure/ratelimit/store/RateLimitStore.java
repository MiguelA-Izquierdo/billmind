package dev.izquierdo.billmind._shared.infrastructure.ratelimit.store;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitDecision;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;

/**
 * Atomically consumes {@code policy.cost()} tokens from the bucket identified by {@code key},
 * returning the decision. The port keeps the rate-limiting library out of the application layer:
 * a Caffeine-backed adapter runs in-process today, a Redis-backed one shares buckets across
 * instances later — both behind this interface, mirroring {@code ToolResultCache}.
 *
 * <p>The atomicity of the read-modify-write is the adapter's responsibility (in-JVM for Caffeine,
 * a server-side script for Redis); callers never see a partial update. On backend failure the
 * adapter propagates, and the caller applies the policy's {@link RateLimitPolicy#failMode()}.
 */
public interface RateLimitStore {

    RateLimitDecision tryConsume(String key, RateLimitPolicy policy);
}
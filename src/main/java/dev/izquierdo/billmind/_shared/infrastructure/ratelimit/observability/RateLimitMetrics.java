package dev.izquierdo.billmind._shared.infrastructure.ratelimit.observability;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes rate-limit telemetry as Micrometer meters on the Actuator {@code /metrics} and
 * {@code /prometheus} endpoints, mirroring {@code MetricsLlmTelemetry}. Tags stay low-cardinality:
 * the bucket key (which carries an IP, session id, or token hash) is <em>never</em> a tag.
 *
 * <ul>
 *   <li>{@code ratelimit.requests} — Counter, tagged {@code profile}, {@code phase}, {@code outcome}
 *       ({@code allowed|throttled|unavailable}); a throttle rate is {@code outcome=throttled} over total.</li>
 *   <li>{@code ratelimit.store.errors} — Counter, tagged {@code profile}; drives the fail-mode alert
 *       so a silent store outage never hides an open (fail-open) or blanket-denied (fail-closed) window.</li>
 * </ul>
 */
@Component
public class RateLimitMetrics {

    private final MeterRegistry registry;

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOutcome(RateLimitProfile profile, RateLimitPhase phase, RateLimitVerdict.Status status) {
        registry.counter("ratelimit.requests",
                "profile", profile.name(),
                "phase", phase.name(),
                "outcome", status.name().toLowerCase()).increment();
    }

    public void recordStoreError(RateLimitProfile profile) {
        registry.counter("ratelimit.store.errors", "profile", profile.name()).increment();
    }
}
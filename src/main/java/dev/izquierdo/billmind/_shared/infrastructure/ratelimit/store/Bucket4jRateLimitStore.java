package dev.izquierdo.billmind._shared.infrastructure.ratelimit.store;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitDecision;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link RateLimitStore} backed by bucket4j's {@link ProxyManager}. The {@code ProxyManager} is the
 * seam that swaps Caffeine (in-process) for Redis (shared) without touching this class — it owns the
 * atomic read-modify-write. Here we only translate a {@link RateLimitPolicy} into a bucket4j
 * {@code Bandwidth} and map the {@link ConsumptionProbe} back to a {@link RateLimitDecision}.
 */
@Component
public class Bucket4jRateLimitStore implements RateLimitStore {

    private final ProxyManager<String> proxyManager;

    public Bucket4jRateLimitStore(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public RateLimitDecision tryConsume(String key, RateLimitPolicy policy) {
        BucketProxy bucket = proxyManager.getProxy(key, () -> configuration(policy));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(policy.cost());
        if (probe.isConsumed()) {
            return RateLimitDecision.allowed(probe.getRemainingTokens(), policy.capacity());
        }
        return RateLimitDecision.denied(policy.capacity(), Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private BucketConfiguration configuration(RateLimitPolicy policy) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.refillTokens(), policy.refillPeriod())
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
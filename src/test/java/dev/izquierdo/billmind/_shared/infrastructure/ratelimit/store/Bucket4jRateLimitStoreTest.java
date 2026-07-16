package dev.izquierdo.billmind._shared.infrastructure.ratelimit.store;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitDecision;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the adapter against a real in-process bucket4j {@link CaffeineProxyManager} — no mocks,
 * no Spring — so the policy→Bandwidth translation and probe→decision mapping are covered end to end.
 * Refill periods are long enough that no refill occurs mid-test.
 */
class Bucket4jRateLimitStoreTest {

    private RateLimitStore store;

    @BeforeEach
    void setUp() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(1000);
        ProxyManager<String> proxyManager = new CaffeineProxyManager<>(builder, Duration.ofMinutes(10));
        store = new Bucket4jRateLimitStore(proxyManager);
    }

    @Test
    void shouldAllowUpToCapacityThenThrottle() {
        RateLimitPolicy policy = policy(3, 3, 1);

        assertThat(store.tryConsume("k", policy).remainingTokens()).isEqualTo(2);
        assertThat(store.tryConsume("k", policy).remainingTokens()).isEqualTo(1);
        assertThat(store.tryConsume("k", policy).remainingTokens()).isEqualTo(0);

        RateLimitDecision denied = store.tryConsume("k", policy);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.limit()).isEqualTo(3);
        assertThat(denied.retryAfter()).isPositive();
    }

    @Test
    void shouldDrawCostTokensPerRequest() {
        RateLimitPolicy policy = policy(5, 5, 5);

        RateLimitDecision first = store.tryConsume("k", policy);
        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingTokens()).isZero();

        assertThat(store.tryConsume("k", policy).allowed()).isFalse();
    }

    @Test
    void shouldIsolateBucketsByKey() {
        RateLimitPolicy policy = policy(1, 1, 1);

        assertThat(store.tryConsume("session-a", policy).allowed()).isTrue();
        assertThat(store.tryConsume("session-a", policy).allowed()).isFalse();
        assertThat(store.tryConsume("session-b", policy).allowed()).isTrue();
    }

    private RateLimitPolicy policy(long capacity, long refillTokens, long cost) {
        return new RateLimitPolicy(RateLimitProfile.CHAT, capacity, refillTokens, Duration.ofHours(1), cost);
    }
}
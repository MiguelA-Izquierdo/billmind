package dev.izquierdo.billmind._shared.infrastructure.ratelimit.store;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the bucket4j {@link ProxyManager} backing the rate limiter. Today it is Caffeine-backed and
 * in-process — limits are therefore <em>per instance</em>, correct for the single-instance Phase 1
 * deployment. A Redis-backed {@code ProxyManager} bean (Lettuce) will later be selected by
 * {@code billmind.ratelimit.store=redis} to make limits global, with no change to the store adapter,
 * policies, or filters. See {@code docs/RATELIMIT.md}.
 *
 * <p>The {@code expireAfter} duration is handed to bucket4j (not set on the Caffeine builder, which
 * would clash with bucket4j's own expiry policy): idle buckets are evicted after that long, so the
 * map does not grow unbounded with one entry per key ever seen.
 */
@Configuration
public class ProxyManagerConfig {

    @Bean
    @ConditionalOnProperty(name = "billmind.ratelimit.store", havingValue = "caffeine", matchIfMissing = true)
    public ProxyManager<String> caffeineProxyManager(
            @Value("${billmind.ratelimit.caffeine.max-size:50000}") long maxSize,
            @Value("${billmind.ratelimit.caffeine.expire-after:PT1H}") Duration expireAfter) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maxSize);
        return new CaffeineProxyManager<>(builder, expireAfter);
    }
}
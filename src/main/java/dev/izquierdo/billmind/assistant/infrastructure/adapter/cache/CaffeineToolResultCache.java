package dev.izquierdo.billmind.assistant.infrastructure.adapter.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * In-process {@link ToolResultCache} backed by Caffeine. Bounded by size and a write-based TTL,
 * mirroring the horizon of {@code InMemoryAssistantRepository} (conversations live {@code PT2H}).
 *
 * <p>Unlike the conversation store's sliding (access-based) TTL, this cache expires each entry
 * {@code ttl} after it was <em>written</em>: for a content cache we want bounded staleness — a
 * change to the regulatory knowledge base is reflected within {@code ttl} regardless of how often
 * a query is hit — rather than keeping a popular entry pinned forever. Only wired when the agentic
 * assistant is active ({@code assistant.tools.enabled=true}); a Redis-backed implementation will
 * replace it for multi-instance deployments behind the same interface.
 */
@Component
@ConditionalOnProperty(name = "assistant.tools.enabled", havingValue = "true")
public class CaffeineToolResultCache implements ToolResultCache {

    private final Cache<String, CachedToolResult> cache;

    public CaffeineToolResultCache(
            @Value("${assistant.tools.cache.max-size:500}") long maxSize,
            @Value("${assistant.tools.cache.ttl:PT2H}") Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public Optional<CachedToolResult> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void put(String key, CachedToolResult value) {
        cache.put(key, value);
    }
}
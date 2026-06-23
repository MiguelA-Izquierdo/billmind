package dev.izquierdo.billmind.assistant.infrastructure.persistence;

import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation store for Phase 1 (no user accounts, no persistence).
 *
 * <p>Bounded by a sliding TTL and a hard cap on the number of conversations so the map cannot grow
 * without limit in a long-running process. A conversation's TTL is refreshed on every read
 * ({@code findById}) and write ({@code save}); idle conversations expire and are evicted lazily on
 * the next {@code save}. When the cap is exceeded, the least-recently-accessed conversations are
 * evicted first.
 */
@Repository
public class InMemoryAssistantRepository implements AssistantRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAssistantRepository.class);

    private final Map<UUID, Entry> store = new ConcurrentHashMap<>();
    private final int maxSize;
    private final Duration ttl;

    public InMemoryAssistantRepository(
            @Value("${assistant.conversation.max-size:1000}") int maxSize,
            @Value("${assistant.conversation.ttl:PT2H}") Duration ttl) {
        this.maxSize = maxSize;
        this.ttl     = ttl;
    }

    @Override
    public void save(Conversation conversation) {
        Instant now = Instant.now();
        store.put(conversation.getId(), new Entry(conversation, now));
        evictExpired(now);
        evictOverflow();
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        Instant now = Instant.now();
        Entry refreshed = store.computeIfPresent(id, (key, existing) ->
                isExpired(existing, now) ? null : new Entry(existing.conversation(), now));
        return Optional.ofNullable(refreshed).map(Entry::conversation);
    }

    private void evictExpired(Instant now) {
        store.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private void evictOverflow() {
        int overflow = store.size() - maxSize;
        if (overflow <= 0) return;
        List<UUID> coldest = store.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().lastAccess()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList();
        coldest.forEach(store::remove);
        log.debug("Evicted {} conversation(s) over the {}-entry cap", coldest.size(), maxSize);
    }

    private boolean isExpired(Entry entry, Instant now) {
        return entry.lastAccess().plus(ttl).isBefore(now);
    }

    private record Entry(Conversation conversation, Instant lastAccess) {}
}
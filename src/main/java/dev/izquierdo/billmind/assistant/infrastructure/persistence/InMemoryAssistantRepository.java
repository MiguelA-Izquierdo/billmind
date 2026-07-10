package dev.izquierdo.billmind.assistant.infrastructure.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory conversation store for Phase 1 (no user accounts, no persistence).
 *
 * <p>Backed by Caffeine: a sliding (access-based) TTL refreshed on every read ({@code findById})
 * and write ({@code save}) so idle conversations expire, plus a hard cap on the number of
 * conversations so the store cannot grow without limit in a long-running process. When the cap is
 * exceeded, Caffeine evicts the least-valuable (approximately least-recently-used) entries.
 */
@Repository
public class InMemoryAssistantRepository implements AssistantRepository {

    private final Cache<UUID, Conversation> store;

    public InMemoryAssistantRepository(
            @Value("${assistant.conversation.max-size:1000}") long maxSize,
            @Value("${assistant.conversation.ttl:PT2H}") Duration ttl) {
        this.store = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(ttl)
                .build();
    }

    @Override
    public void save(Conversation conversation) {
        store.put(conversation.getId(), conversation);
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return Optional.ofNullable(store.getIfPresent(id));
    }
}
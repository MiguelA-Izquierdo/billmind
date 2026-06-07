package dev.izquierdo.billmind.assistant.infrastructure.persistence;

import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAssistantRepository implements AssistantRepository {

    private final Map<UUID, Conversation> store = new ConcurrentHashMap<>();

    @Override
    public void save(Conversation conversation) {
        store.put(conversation.getId(), conversation);
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }
}
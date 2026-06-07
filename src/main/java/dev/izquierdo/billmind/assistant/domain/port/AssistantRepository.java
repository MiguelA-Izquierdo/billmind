package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.assistant.domain.model.Conversation;

import java.util.Optional;
import java.util.UUID;

public interface AssistantRepository {
    void save(Conversation conversation);
    Optional<Conversation> findById(UUID id);
}
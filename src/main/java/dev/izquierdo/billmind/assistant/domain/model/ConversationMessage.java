package dev.izquierdo.billmind.assistant.domain.model;

import java.time.Instant;
import java.util.UUID;

public class ConversationMessage {

    private final UUID id;
    private final MessageRole role;
    private final String content;
    private final Instant createdAt;

    private ConversationMessage(UUID id, MessageRole role, String content, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ConversationMessage create(MessageRole role, String content) {
        return new ConversationMessage(UUID.randomUUID(), role, content, Instant.now());
    }

    public static ConversationMessage reconstitute(UUID id, MessageRole role, String content, Instant createdAt) {
        return new ConversationMessage(id, role, content, createdAt);
    }

    public UUID getId()            { return id; }
    public MessageRole getRole()   { return role; }
    public String getContent()     { return content; }
    public Instant getCreatedAt()  { return createdAt; }
}
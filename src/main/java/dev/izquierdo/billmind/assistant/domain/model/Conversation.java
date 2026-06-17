package dev.izquierdo.billmind.assistant.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Conversation {

    private final UUID id;
    private final UUID sessionId;
    private final UUID invoiceId;
    private final Instant createdAt;
    private final List<ConversationMessage> messages;

    private Conversation(UUID id, UUID sessionId, UUID invoiceId,
                         Instant createdAt, List<ConversationMessage> messages) {
        this.id        = id;
        this.sessionId = sessionId;
        this.invoiceId = invoiceId;
        this.createdAt = createdAt;
        this.messages  = new ArrayList<>(messages);
    }

    public static Conversation create(UUID sessionId, UUID invoiceId) {
        return new Conversation(UUID.randomUUID(), sessionId, invoiceId, Instant.now(), List.of());
    }

    public static Conversation reconstitute(UUID id, UUID sessionId, UUID invoiceId,
                                            Instant createdAt, List<ConversationMessage> messages) {
        return new Conversation(id, sessionId, invoiceId, createdAt, messages);
    }

    public void addMessage(ConversationMessage message) {
        messages.add(message);
    }

    public UUID getId()                                  { return id; }
    public UUID getSessionId()                           { return sessionId; }
    public UUID getInvoiceId()                           { return invoiceId; }
    public Instant getCreatedAt()                        { return createdAt; }
    public List<ConversationMessage> getMessages()       { return Collections.unmodifiableList(messages); }

    public List<ConversationMessage> getRecentMessages(int maxMessages) {
        int from = Math.max(0, messages.size() - maxMessages);
        return Collections.unmodifiableList(messages.subList(from, messages.size()));
    }
}
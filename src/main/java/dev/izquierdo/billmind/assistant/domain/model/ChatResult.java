package dev.izquierdo.billmind.assistant.domain.model;

import java.util.List;
import java.util.UUID;

public record ChatResult(
    UUID conversationId,
    String answer,
    List<ChatCitation> citations
) {
    public record ChatCitation(String title, String source, String docType) {}
}
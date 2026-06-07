package dev.izquierdo.billmind.assistant.domain.model;

import java.util.List;

public record ChatResult(
    String answer,
    List<ChatCitation> citations
) {
    public record ChatCitation(String title, String source, String docType) {}
}
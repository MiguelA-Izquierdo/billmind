package dev.izquierdo.billmind.assistant.domain.model;

public record RegulatorySnippet(
        String title,
        String source,
        String docType,
        String content
) {}
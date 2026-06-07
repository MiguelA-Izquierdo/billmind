package dev.izquierdo.billmind.knowledge.domain.model;

import java.util.UUID;

public record KnowledgeSearchResult(
        UUID chunkId,
        UUID documentId,
        DocType docType,
        String title,
        String source,
        String section,
        String content,
        double score
) {}
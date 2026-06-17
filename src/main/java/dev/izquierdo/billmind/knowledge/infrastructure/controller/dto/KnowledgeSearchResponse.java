package dev.izquierdo.billmind.knowledge.infrastructure.controller.dto;

import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;

import java.util.UUID;

public record KnowledgeSearchResponse(
        UUID chunkId,
        UUID documentId,
        String docType,
        String supplyDomain,
        String title,
        String source,
        String section,
        String content,
        double score
) {
    public static KnowledgeSearchResponse from(KnowledgeSearchResult result) {
        return new KnowledgeSearchResponse(
                result.chunkId(),
                result.documentId(),
                result.docType().name(),
                result.supplyDomain().name(),
                result.title(),
                result.source(),
                result.section(),
                result.content(),
                result.score()
        );
    }
}
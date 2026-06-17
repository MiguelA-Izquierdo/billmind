package dev.izquierdo.billmind.knowledge.domain.model;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.util.UUID;

public record KnowledgeSearchResult(
        UUID chunkId,
        UUID documentId,
        DocType docType,
        SupplyDomain supplyDomain,
        String title,
        String source,
        String section,
        String content,
        double score
) {}
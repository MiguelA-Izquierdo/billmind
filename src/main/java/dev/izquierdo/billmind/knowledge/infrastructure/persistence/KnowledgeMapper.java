package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;

class KnowledgeMapper {

    static KnowledgeDocumentEntity toDocEntity(KnowledgeDocument doc) {
        KnowledgeDocumentEntity e = new KnowledgeDocumentEntity();
        e.id        = doc.getId();
        e.docType   = doc.getDocType();
        e.title     = doc.getTitle();
        e.source    = doc.getSource();
        e.validFrom = doc.getValidFrom();
        e.validTo   = doc.getValidTo();
        e.createdAt = doc.getCreatedAt();
        return e;
    }

    static KnowledgeChunkEntity toChunkEntity(KnowledgeChunk chunk, String embeddingId) {
        KnowledgeChunkEntity e = new KnowledgeChunkEntity();
        e.id          = chunk.getId();
        e.documentId  = chunk.getDocumentId();
        e.embeddingId = embeddingId;
        e.content     = chunk.getContent();
        e.section     = chunk.getSection();
        e.chunkIndex  = chunk.getChunkIndex();
        return e;
    }
}
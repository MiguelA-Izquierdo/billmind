package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunkEntity {

    @Id
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(name = "document_id", nullable = false)
    UUID documentId;

    @Column(name = "embedding_id", nullable = false)
    String embeddingId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    String content;

    @Column(name = "section")
    String section;

    @Column(name = "chunk_index", nullable = false)
    int chunkIndex;

    protected KnowledgeChunkEntity() {}

    public UUID getId()           { return id; }
    public UUID getDocumentId()   { return documentId; }
    public String getEmbeddingId(){ return embeddingId; }
    public String getContent()    { return content; }
    public String getSection()    { return section; }
    public int getChunkIndex()    { return chunkIndex; }
}
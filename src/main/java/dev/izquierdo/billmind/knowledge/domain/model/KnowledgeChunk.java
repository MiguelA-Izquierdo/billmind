package dev.izquierdo.billmind.knowledge.domain.model;

import java.util.Objects;
import java.util.UUID;

public class KnowledgeChunk {

    private final UUID id;
    private final UUID documentId;
    private final String content;
    private final String section;
    private final int chunkIndex;

    private KnowledgeChunk(UUID id, UUID documentId, String content, String section, int chunkIndex) {
        this.id          = Objects.requireNonNull(id,         "id cannot be null");
        this.documentId  = Objects.requireNonNull(documentId, "documentId cannot be null");
        this.content     = Objects.requireNonNull(content,    "content cannot be null");
        this.section     = section;
        this.chunkIndex  = chunkIndex;

        if (this.content.isBlank()) throw new IllegalArgumentException("content cannot be blank");
        if (chunkIndex < 0)         throw new IllegalArgumentException("chunkIndex must be >= 0");
    }

    public static KnowledgeChunk create(UUID id, UUID documentId, String content, String section, int chunkIndex) {
        return new KnowledgeChunk(id, documentId, content, section, chunkIndex);
    }

    public static KnowledgeChunk create(UUID id, UUID docId, String content, int index) {
        return new KnowledgeChunk(id, docId, content, null, index);
    }

    public UUID getId()         { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getContent()  { return content; }
    public String getSection()  { return section; }
    public int getChunkIndex()  { return chunkIndex; }
}
package dev.izquierdo.billmind.knowledge.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class KnowledgeDocument {

    private final UUID id;
    private final DocType docType;
    private final String title;
    private final String source;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final Instant createdAt;

    private KnowledgeDocument(UUID id, DocType docType, String title, String source,
                               LocalDate validFrom, LocalDate validTo, Instant createdAt) {
        this.id        = Objects.requireNonNull(id,      "id cannot be null");
        this.docType   = Objects.requireNonNull(docType, "docType cannot be null");
        this.title     = Objects.requireNonNull(title,   "title cannot be null");
        this.source    = Objects.requireNonNull(source,  "source cannot be null");
        this.validFrom = validFrom;
        this.validTo   = validTo;
        this.createdAt = createdAt != null ? createdAt : Instant.now();

        if (this.title.isBlank())  throw new IllegalArgumentException("title cannot be blank");
        if (this.source.isBlank()) throw new IllegalArgumentException("source cannot be blank");
        if (this.validTo != null && this.validFrom != null && this.validTo.isBefore(this.validFrom))
            throw new IllegalArgumentException("validTo cannot be before validFrom");
    }

    public static KnowledgeDocument create(UUID id, DocType docType, String title, String source,
                                           LocalDate validFrom, LocalDate validTo) {
        return new KnowledgeDocument(id, docType, title, source, validFrom, validTo, Instant.now());
    }

    public static KnowledgeDocument reconstitute(UUID id, DocType docType, String title, String source,
                                                  LocalDate validFrom, LocalDate validTo, Instant createdAt) {
        return new KnowledgeDocument(id, docType, title, source, validFrom, validTo, createdAt);
    }

    public UUID getId()          { return id; }
    public DocType getDocType()  { return docType; }
    public String getTitle()     { return title; }
    public String getSource()    { return source; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo()   { return validTo; }
    public Instant getCreatedAt()   { return createdAt; }
}
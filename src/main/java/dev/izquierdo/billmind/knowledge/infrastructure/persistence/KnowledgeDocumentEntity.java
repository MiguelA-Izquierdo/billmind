package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentEntity {

    @Id
    @Column(updatable = false, nullable = false)
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    DocType docType;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_domain", nullable = false, length = 15)
    SupplyDomain supplyDomain;

    @Column(name = "title", nullable = false)
    String title;

    @Column(name = "source", nullable = false)
    String source;

    @Column(name = "valid_from")
    LocalDate validFrom;

    @Column(name = "valid_to")
    LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected KnowledgeDocumentEntity() {}

    public UUID getId()                    { return id; }
    public DocType getDocType()            { return docType; }
    public SupplyDomain getSupplyDomain()  { return supplyDomain; }
    public String getTitle()               { return title; }
    public String getSource()              { return source; }
    public LocalDate getValidFrom()        { return validFrom; }
    public LocalDate getValidTo()          { return validTo; }
    public Instant getCreatedAt()          { return createdAt; }
}
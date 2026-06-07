package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnowledgeDocumentJpaRepository extends JpaRepository<KnowledgeDocumentEntity, UUID> {
}
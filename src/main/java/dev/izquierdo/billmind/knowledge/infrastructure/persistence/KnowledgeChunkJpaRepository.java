package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkJpaRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {

    @Query(value = """
            SELECT * FROM knowledge_chunks
            WHERE to_tsvector('spanish', unaccent(content)) @@ plainto_tsquery('spanish', unaccent(:query))
            ORDER BY ts_rank(to_tsvector('spanish', unaccent(content)), plainto_tsquery('spanish', unaccent(:query))) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeChunkEntity> searchByContent(@Param("query") String query, @Param("limit") int limit);

    List<KnowledgeChunkEntity> findByDocumentId(UUID documentId);

    List<KnowledgeChunkEntity> findByEmbeddingIdIn(Collection<String> embeddingIds);
}
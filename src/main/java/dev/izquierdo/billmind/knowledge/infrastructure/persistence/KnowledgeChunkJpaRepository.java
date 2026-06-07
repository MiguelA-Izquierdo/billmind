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
            WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', :query)
            ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeChunkEntity> searchByContent(@Param("query") String query, @Param("limit") int limit);

    List<KnowledgeChunkEntity> findByEmbeddingIdIn(Collection<String> embeddingIds);
}
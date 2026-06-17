package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkJpaRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {

    // OR-semantics BM25: extracts stemmed lexemes from the query tsvector and joins them with |,
    // so a long conversational message matches any chunk that contains any relevant term.
    // plainto_tsquery (AND) would require every term to appear in a single chunk, returning 0
    // results for multi-sentence user messages.
    @Query(value = """
            SELECT * FROM knowledge_chunks
            WHERE to_tsvector('spanish', unaccent(content)) @@ (
                SELECT string_agg(lexeme, ' | ')::tsquery
                FROM unnest(to_tsvector('spanish', unaccent(:query)))
            )
            ORDER BY ts_rank(
                to_tsvector('spanish', unaccent(content)),
                (SELECT string_agg(lexeme, ' | ')::tsquery FROM unnest(to_tsvector('spanish', unaccent(:query))))
            ) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeChunkEntity> searchByContent(@Param("query") String query, @Param("limit") int limit);

    List<KnowledgeChunkEntity> findByDocumentId(UUID documentId);

    List<KnowledgeChunkEntity> findByEmbeddingIdIn(Collection<String> embeddingIds);
}
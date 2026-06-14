package dev.izquierdo.billmind.knowledge.infrastructure.adapter;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeSearchRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeChunkEntity;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeChunkJpaRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeDocumentEntity;
import dev.izquierdo.billmind.knowledge.infrastructure.persistence.KnowledgeDocumentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class HybridKnowledgeSearchRepository implements KnowledgeSearchRepository {

    private static final Logger  log       = LoggerFactory.getLogger(HybridKnowledgeSearchRepository.class);
    private static final int    RRF_K     = 60;
    private static final double MIN_SCORE = 0.01;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeChunkJpaRepository chunkJpa;
    private final KnowledgeDocumentJpaRepository docJpa;

    public HybridKnowledgeSearchRepository(EmbeddingStore<TextSegment> embeddingStore,
                                            EmbeddingModel embeddingModel,
                                            KnowledgeChunkJpaRepository chunkJpa,
                                            KnowledgeDocumentJpaRepository docJpa) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.chunkJpa       = chunkJpa;
        this.docJpa         = docJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> search(String query, int maxResults) {
        int candidateSize = maxResults * 3;

        // 1. Vector search
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> vectorMatches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(candidateSize)
                        .build()
        ).matches();

        // 2. BM25 full-text search
        List<KnowledgeChunkEntity> textMatches = chunkJpa.searchByContent(query, candidateSize);

        // 3. Reciprocal Rank Fusion
        return reciprocalRankFusion(vectorMatches, textMatches, maxResults);
    }

    private List<KnowledgeSearchResult> reciprocalRankFusion(
            List<EmbeddingMatch<TextSegment>> vectorMatches,
            List<KnowledgeChunkEntity> textMatches,
            int maxResults) {

        Map<String, Double> scores     = new LinkedHashMap<>();
        Map<String, KnowledgeChunkEntity> entityByEmbId = new HashMap<>();

        // Score from vector ranking
        for (int i = 0; i < vectorMatches.size(); i++) {
            String embId = vectorMatches.get(i).embeddingId();
            scores.merge(embId, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        // Score from text ranking
        for (int i = 0; i < textMatches.size(); i++) {
            String embId = textMatches.get(i).getEmbeddingId();
            scores.merge(embId, 1.0 / (RRF_K + i + 1), Double::sum);
            entityByEmbId.put(embId, textMatches.get(i));
        }

        // Resolve chunk entities for vector-only matches
        Set<String> unresolvedIds = vectorMatches.stream()
                .map(EmbeddingMatch::embeddingId)
                .filter(id -> !entityByEmbId.containsKey(id))
                .collect(Collectors.toSet());

        if (!unresolvedIds.isEmpty()) {
            chunkJpa.findByEmbeddingIdIn(unresolvedIds)
                    .forEach(c -> entityByEmbId.put(c.getEmbeddingId(), c));
        }

        // Load parent documents in one query
        Set<UUID> docIds = entityByEmbId.values().stream()
                .map(KnowledgeChunkEntity::getDocumentId).collect(Collectors.toSet());
        Map<UUID, KnowledgeDocumentEntity> docById = docJpa.findAllById(docIds).stream()
                .collect(Collectors.toMap(KnowledgeDocumentEntity::getId, d -> d));

        // Build sorted results
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .filter(e -> e.getValue() >= MIN_SCORE)
                .limit(maxResults)
                .filter(e -> entityByEmbId.containsKey(e.getKey()))
                .map(e -> {
                    KnowledgeChunkEntity chunk = entityByEmbId.get(e.getKey());
                    KnowledgeDocumentEntity doc = docById.get(chunk.getDocumentId());
                    return toResult(chunk, doc, e.getValue());
                })
                .toList();
    }

    private KnowledgeSearchResult toResult(KnowledgeChunkEntity chunk,
                                            KnowledgeDocumentEntity doc,
                                            double score) {
        if (doc == null) {
            log.warn("Orphaned chunk detected: chunkId={} references missing documentId={}",
                    chunk.getId(), chunk.getDocumentId());
        }
        return new KnowledgeSearchResult(
                chunk.getId(),
                chunk.getDocumentId(),
                doc != null ? doc.getDocType() : DocType.GENERAL,
                doc != null ? doc.getTitle()   : "Desconocido",
                doc != null ? doc.getSource()  : "",
                chunk.getSection(),
                chunk.getContent(),
                score
        );
    }
}
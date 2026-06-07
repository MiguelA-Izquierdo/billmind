package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaKnowledgeRepository implements KnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaKnowledgeRepository.class);

    private final KnowledgeDocumentJpaRepository docJpa;
    private final KnowledgeChunkJpaRepository chunkJpa;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public JpaKnowledgeRepository(KnowledgeDocumentJpaRepository docJpa,
                                   KnowledgeChunkJpaRepository chunkJpa,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   EmbeddingModel embeddingModel) {
        this.docJpa         = docJpa;
        this.chunkJpa       = chunkJpa;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    @Transactional
    public void save(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        docJpa.save(KnowledgeMapper.toDocEntity(document));
        log.debug("Knowledge document saved: id={} title={} chunks={}", document.getId(), document.getTitle(), chunks.size());

        for (KnowledgeChunk chunk : chunks) {
            dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata()
                    .put("doc_id",   document.getId().toString())
                    .put("doc_type", document.getDocType().name())
                    .put("title",    document.getTitle())
                    .put("source",   document.getSource())
                    .put("section",  chunk.getSection() != null ? chunk.getSection() : "");

            TextSegment segment  = TextSegment.from(chunk.getContent(), metadata);
            Embedding embedding  = embeddingModel.embed(segment.text()).content();
            String embeddingId   = embeddingStore.add(embedding, segment);

            chunkJpa.save(KnowledgeMapper.toChunkEntity(chunk, embeddingId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return docJpa.count() == 0;
    }

    @Override
    @Transactional
    public void deleteAll() {
        long chunks = chunkJpa.count();
        long docs   = docJpa.count();
        chunkJpa.deleteAll();
        docJpa.deleteAll();
        log.info("Knowledge base cleared: {} documents, {} chunks removed", docs, chunks);
    }
}
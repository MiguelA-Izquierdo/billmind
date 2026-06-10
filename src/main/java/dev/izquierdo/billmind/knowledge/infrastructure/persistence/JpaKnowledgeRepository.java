package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class JpaKnowledgeRepository implements KnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaKnowledgeRepository.class);
    private static final int INDEX_MIN_ROWS = 100;

    private final KnowledgeDocumentJpaRepository docJpa;
    private final KnowledgeChunkJpaRepository chunkJpa;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DataSource dataSource;
    private final String vectorTable;

    public JpaKnowledgeRepository(KnowledgeDocumentJpaRepository docJpa,
                                   KnowledgeChunkJpaRepository chunkJpa,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   EmbeddingModel embeddingModel,
                                   DataSource dataSource,
                                   @Value("${pgvector.table-name:vector_store}") String vectorTable) {
        this.docJpa         = docJpa;
        this.chunkJpa       = chunkJpa;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.dataSource     = dataSource;
        this.vectorTable    = vectorTable;
    }

    @PostConstruct
    public void ensureExtensions() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            log.debug("PostgreSQL unaccent extension ensured");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create unaccent extension: " + e.getMessage(), e);
        }
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

            TextSegment segment    = TextSegment.from(chunk.getContent(), metadata);
            Embedding embedding    = embeddingModel.embed(segment.text()).content();
            String embeddingId     = embeddingStore.add(embedding, segment);

            verifyEmbeddingStored(embeddingId, document.getTitle());
            chunkJpa.save(KnowledgeMapper.toChunkEntity(chunk, embeddingId));
        }
    }

    // PgVectorEmbeddingStore uses its own JDBC connection outside the Spring transaction,
    // so failures are not propagated as exceptions — verify the row was actually committed.
    private void verifyEmbeddingStored(String embeddingId, String docTitle) {
        String sql = "SELECT COUNT(*) FROM " + vectorTable + " WHERE embedding_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, java.util.UUID.fromString(embeddingId));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    throw new IllegalStateException(String.format(
                            "Embedding was not persisted to '%s' for document '%s' (id=%s). " +
                            "Check embedding model connectivity and vector store configuration.",
                            vectorTable, docTitle, embeddingId));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not verify embedding storage: " + e.getMessage(), e);
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
        embeddingStore.removeAll();
        log.info("Knowledge base cleared: {} documents, {} chunks, all vectors removed", docs, chunks);
    }

    @Override
    public long rebuildIndex() {
        String indexName = vectorTable + "_embedding_idx";
        try (Connection conn = dataSource.getConnection()) {
            long rows = countRows(conn);
            if (rows < INDEX_MIN_ROWS) {
                log.info("IVFFlat index skipped — {} vectors (need ≥{})", rows, INDEX_MIN_ROWS);
                return 0;
            }
            int lists = (int) Math.sqrt(rows);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP INDEX IF EXISTS " + indexName);
                stmt.execute("CREATE INDEX " + indexName +
                        " ON " + vectorTable + " USING ivfflat (embedding vector_cosine_ops)" +
                        " WITH (lists = " + lists + ")");
            }
            log.info("IVFFlat index rebuilt: table={}, rows={}, lists={}", vectorTable, rows, lists);
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to rebuild IVFFlat index: " + e.getMessage(), e);
        }
    }

    private long countRows(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + vectorTable)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
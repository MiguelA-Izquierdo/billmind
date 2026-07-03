package dev.izquierdo.billmind.knowledge.infrastructure.persistence;

import dev.langchain4j.data.document.Metadata;
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

import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        if (!vectorTable.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid vector table name (alphanumeric + underscore only): " + vectorTable);
        }
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
    public void upsert(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        deleteByDocumentId(document.getId());

        docJpa.save(KnowledgeMapper.toDocEntity(document));
        log.debug("Knowledge document saved: id={} title={} chunks={}", document.getId(), document.getTitle(), chunks.size());

        List<TextSegment> segments = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            segments.add(TextSegment.from(chunk.getContent(), buildMetadata(document, chunk)));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        List<String> embeddingIds  = embeddingStore.addAll(embeddings, segments);

        // embeddingStore operates outside the Spring transaction (auto-commit).
        // If anything below fails, compensate by deleting the embeddings we just wrote.
        try {
            verifyAllEmbeddingsStored(embeddingIds, document.getTitle());

            List<KnowledgeChunkEntity> entities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                entities.add(KnowledgeMapper.toChunkEntity(chunks.get(i), embeddingIds.get(i)));
            }
            chunkJpa.saveAll(entities);
        } catch (Exception e) {
            log.error("Upsert failed after embedding write — compensating deletion of {} embeddings for docId={}",
                    embeddingIds.size(), document.getId());
            deleteEmbeddingsByIds(embeddingIds);
            throw e;
        }
    }

    private Metadata buildMetadata(KnowledgeDocument document, KnowledgeChunk chunk) {
        return new Metadata()
                .put("doc_id",   document.getId().toString())
                .put("doc_type", document.getDocType().name())
                .put("title",    document.getTitle())
                .put("source",   document.getSource())
                .put("section",  chunk.getSection() != null ? chunk.getSection() : "");
    }

    // PgVectorEmbeddingStore uses its own JDBC connection outside the Spring transaction,
    // so failures are not propagated as exceptions — verify the rows were actually committed.
    // DataSourceUtils.getConnection() reuses the transaction-bound connection; READ COMMITTED
    // (PostgreSQL default) guarantees visibility of the embedding store's auto-committed inserts.
    private void verifyAllEmbeddingsStored(List<String> ids, String docTitle) {
        String sql = "SELECT COUNT(*) FROM " + vectorTable + " WHERE embedding_id = ANY(?)";
        UUID[] uuids = ids.stream().map(UUID::fromString).toArray(UUID[]::new);
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            Array pgArray = conn.createArrayOf("uuid", uuids);
            try {
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setArray(1, pgArray);
                    ResultSet rs = stmt.executeQuery();
                    try {
                        rs.next();
                        int count = rs.getInt(1);
                        if (count != ids.size()) {
                            throw new IllegalStateException(String.format(
                                    "Only %d of %d embeddings were persisted to '%s' for document '%s'. " +
                                    "Check embedding model connectivity and vector store configuration.",
                                    count, ids.size(), vectorTable, docTitle));
                        }
                    } finally {
                        rs.close();
                    }
                } finally {
                    stmt.close();
                }
            } finally {
                pgArray.free();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not verify embedding storage: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private void deleteByDocumentId(UUID docId) {
        List<KnowledgeChunkEntity> existingChunks = chunkJpa.findByDocumentId(docId);
        if (!existingChunks.isEmpty()) {
            deleteEmbeddingsByIds(existingChunks.stream().map(c -> c.embeddingId).toList());
            chunkJpa.deleteAll(existingChunks);
            log.debug("Deleted {} chunks for docId={}", existingChunks.size(), docId);
        }
        docJpa.deleteById(docId);
    }

    private void deleteEmbeddingsByIds(List<String> embeddingIds) {
        String sql = "DELETE FROM " + vectorTable + " WHERE embedding_id = ANY(?)";
        Object[] uuids = embeddingIds.stream().map(UUID::fromString).toArray();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            Array pgArray = conn.createArrayOf("uuid", uuids);
            try {
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setArray(1, pgArray);
                    stmt.executeUpdate();
                } finally {
                    stmt.close();
                }
            } finally {
                pgArray.free();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete embeddings: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
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
            if (!tableExists(conn)) {
                log.info("IVFFlat index skipped — table '{}' does not exist yet", vectorTable);
                return 0;
            }
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

    private boolean tableExists(Connection conn) throws SQLException {
        // to_regclass returns NULL (no exception) when the relation is absent.
        try (PreparedStatement stmt = conn.prepareStatement("SELECT to_regclass(?)")) {
            stmt.setString(1, vectorTable);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getObject(1) != null;
            }
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
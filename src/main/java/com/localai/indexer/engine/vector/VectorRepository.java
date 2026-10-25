package com.localai.indexer.engine.vector;

import com.localai.indexer.ingestion.model.DocumentChunk;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Embedded H2 Vector Repository.
 * Manages chunk storage, schema initialization, and vector similarity search
 * powered by registered H2 SQL ALIAS vector extensions.
 */
@Repository
public class VectorRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializeSchema() {
        log.info("Initializing H2 Vector Extension ALIASes and document schema...");

        // Register custom vector functions in H2 engine
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS COSINE_SIMILARITY FOR \"com.localai.indexer.engine.vector.H2VectorFunctions.cosineSimilarityStrings\";");
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS COSINE_SIMILARITY_BYTES FOR \"com.localai.indexer.engine.vector.H2VectorFunctions.cosineSimilarityBytes\";");
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS DOT_PRODUCT FOR \"com.localai.indexer.engine.vector.H2VectorFunctions.dotProductStrings\";");
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS EUCLIDEAN_DISTANCE FOR \"com.localai.indexer.engine.vector.H2VectorFunctions.euclideanDistanceStrings\";");

        // Create document chunks table with CLOB and VARBINARY vector representations
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS document_chunks (
                id VARCHAR(64) PRIMARY KEY,
                document_id VARCHAR(64) NOT NULL,
                file_path VARCHAR(1024) NOT NULL,
                document_name VARCHAR(255) NOT NULL,
                page_number INT DEFAULT 1,
                chunk_index INT NOT NULL,
                content CLOB NOT NULL,
                vector_json CLOB,
                vector_blob VARBINARY(65536),
                vector_dim INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
        jdbcTemplate.execute(createTableSql);

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_doc_chunks_doc_id ON document_chunks(document_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_doc_chunks_file_path ON document_chunks(file_path);");

        log.info("H2 Vector Schema successfully verified.");
    }

    public void save(DocumentChunk chunk) {
        saveAll(Collections.singletonList(chunk));
    }

    public void saveAll(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        String sql = """
            MERGE INTO document_chunks (id, document_id, file_path, document_name, page_number, chunk_index, content, vector_json, vector_blob, vector_dim)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> batchArgs = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            byte[] blob = H2VectorFunctions.floatsToBytes(chunk.getEmbedding());
            String json = H2VectorFunctions.floatsToString(chunk.getEmbedding());
            int dim = chunk.getEmbedding() != null ? chunk.getEmbedding().length : 0;

            batchArgs.add(new Object[]{
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getFilePath(),
                    chunk.getDocumentName(),
                    chunk.getPageNumber(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    json,
                    blob,
                    dim
            });
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
        log.debug("Persisted {} chunks to H2 vector storage.", chunks.size());
    }

    /**
     * Search nearest chunks using H2 native vector extensions.
     * Supports metrics: "cosine", "dot", "euclidean"
     */
    public List<VectorSearchResult> searchSimilar(float[] queryVector, int topK, String similarityMetric) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return Collections.emptyList();
        }

        String metric = (similarityMetric == null) ? "cosine" : similarityMetric.toLowerCase(Locale.ROOT);
        byte[] queryBytes = H2VectorFunctions.floatsToBytes(queryVector);

        // For maximum speed and exactness, we can score using H2 native alias
        String sql;
        if ("dot".equals(metric)) {
            sql = """
                SELECT id, document_id, file_path, document_name, page_number, chunk_index, content, vector_blob,
                       DOT_PRODUCT(vector_json, ?) AS score
                FROM document_chunks
                ORDER BY score DESC
                LIMIT ?
                """;
            String queryJson = H2VectorFunctions.floatsToString(queryVector);
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapSearchResult(rs, rowNum), queryJson, topK);
        } else if ("euclidean".equals(metric)) {
            sql = """
                SELECT id, document_id, file_path, document_name, page_number, chunk_index, content, vector_blob,
                       EUCLIDEAN_DISTANCE(vector_json, ?) AS dist
                FROM document_chunks
                ORDER BY dist ASC
                LIMIT ?
                """;
            String queryJson = H2VectorFunctions.floatsToString(queryVector);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                double dist = rs.getDouble("dist");
                double score = 1.0 / (1.0 + Math.max(0.0, dist));
                DocumentChunk chunk = mapChunk(rs);
                return new VectorSearchResult(chunk, score);
            }, queryJson, topK);
        } else {
            // Default: Cosine Similarity via fast byte UDF
            sql = """
                SELECT id, document_id, file_path, document_name, page_number, chunk_index, content, vector_blob,
                       COSINE_SIMILARITY_BYTES(vector_blob, ?) AS score
                FROM document_chunks
                ORDER BY score DESC
                LIMIT ?
                """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapSearchResult(rs, rowNum), queryBytes, topK);
        }
    }

    private VectorSearchResult mapSearchResult(ResultSet rs, int rowNum) throws SQLException {
        DocumentChunk chunk = mapChunk(rs);
        double score = rs.getDouble("score");
        return new VectorSearchResult(chunk, score);
    }

    private DocumentChunk mapChunk(ResultSet rs) throws SQLException {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(rs.getString("id"));
        chunk.setDocumentId(rs.getString("document_id"));
        chunk.setFilePath(rs.getString("file_path"));
        chunk.setDocumentName(rs.getString("document_name"));
        chunk.setPageNumber(rs.getInt("page_number"));
        chunk.setChunkIndex(rs.getInt("chunk_index"));
        chunk.setContent(rs.getString("content"));

        byte[] blob = rs.getBytes("vector_blob");
        if (blob != null) {
            chunk.setEmbedding(H2VectorFunctions.bytesToFloats(blob));
        }
        return chunk;
    }

    public List<DocumentChunk> findAll() {
        String sql = "SELECT id, document_id, file_path, document_name, page_number, chunk_index, content, vector_blob FROM document_chunks ORDER BY file_path, chunk_index";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapChunk(rs));
    }

    public List<DocumentChunk> findByDocumentId(String documentId) {
        String sql = "SELECT id, document_id, file_path, document_name, page_number, chunk_index, content, vector_blob FROM document_chunks WHERE document_id = ? ORDER BY chunk_index";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapChunk(rs), documentId);
    }

    public int deleteByDocumentId(String documentId) {
        return jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    public int deleteByFilePath(String filePath) {
        return jdbcTemplate.update("DELETE FROM document_chunks WHERE file_path = ?", filePath);
    }

    public void clearAll() {
        jdbcTemplate.update("DELETE FROM document_chunks");
    }

    public long countChunks() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class);
        return count != null ? count : 0L;
    }

    public long countDistinctDocuments() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT document_id) FROM document_chunks", Long.class);
        return count != null ? count : 0L;
    }

    public List<Map<String, Object>> listIndexedDocuments() {
        String sql = """
            SELECT document_id, document_name, file_path, COUNT(*) AS chunk_count, MAX(page_number) AS max_pages, MIN(created_at) AS indexed_at
            FROM document_chunks
            GROUP BY document_id, document_name, file_path
            ORDER BY indexed_at DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    public static class VectorSearchResult {
        private final DocumentChunk chunk;
        private final double score;

        public VectorSearchResult(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public DocumentChunk getChunk() {
            return chunk;
        }

        public double getScore() {
            return score;
        }
    }
}

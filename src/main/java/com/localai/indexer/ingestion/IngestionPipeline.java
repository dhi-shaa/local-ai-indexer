package com.localai.indexer.ingestion;

import com.localai.indexer.engine.dto.IndexResponseDto;
import com.localai.indexer.engine.vector.VectorRepository;
import com.localai.indexer.ingestion.chunking.ChunkingPipeline;
import com.localai.indexer.ingestion.chunking.ChunkingStrategy;
import com.localai.indexer.ingestion.embedding.EmbeddingService;
import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import com.localai.indexer.ingestion.parser.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Orchestrates full document ingestion:
 * 1. Apache Tika Parsing (PDF, DOCX, TXT)
 * 2. Semantic Chunking (Sentence / Paragraph boundaries)
 * 3. Dense Vector Embedding (Ollama with local fallback)
 * 4. H2 Vector Persistence
 */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final DocumentParser documentParser;
    private final ChunkingPipeline chunkingPipeline;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;

    public IngestionPipeline(DocumentParser documentParser,
                             ChunkingPipeline chunkingPipeline,
                             EmbeddingService embeddingService,
                             VectorRepository vectorRepository) {
        this.documentParser = documentParser;
        this.chunkingPipeline = chunkingPipeline;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    public IndexResponseDto ingest(File file, ChunkingStrategy strategy, int targetChunkSize) {
        IndexResponseDto response = new IndexResponseDto();
        response.setFilePath(file.getAbsolutePath());
        response.setDocumentName(file.getName());

        long t0 = System.currentTimeMillis();

        try {
            // Stage 1: Parse with Apache Tika
            long tParseStart = System.currentTimeMillis();
            ParsedDocument parsedDoc = documentParser.parse(file);
            long parseLatency = System.currentTimeMillis() - tParseStart;

            response.setDocumentId(parsedDoc.getDocumentId());
            response.setPageCount(parsedDoc.getPages().size());
            response.setParseLatencyMs(parseLatency);

            // Stage 2: Semantic Chunking
            long tChunkStart = System.currentTimeMillis();
            List<DocumentChunk> chunks = chunkingPipeline.chunk(parsedDoc, strategy, targetChunkSize);
            long chunkLatency = System.currentTimeMillis() - tChunkStart;
            response.setChunkLatencyMs(chunkLatency);
            response.setChunksCount(chunks.size());

            // Stage 3: Embedding via Ollama (or local semantic fallback)
            long tEmbedStart = System.currentTimeMillis();
            for (DocumentChunk chunk : chunks) {
                float[] embedding = embeddingService.generateEmbedding(chunk.getContent());
                chunk.setEmbedding(embedding);
            }
            long embedLatency = System.currentTimeMillis() - tEmbedStart;
            response.setEmbedLatencyMs(embedLatency);

            // Stage 4: Store in H2 Embedded Vector DB
            long tStoreStart = System.currentTimeMillis();
            // Clean up any previous indexing for this document ID to ensure idempotency
            vectorRepository.deleteByDocumentId(parsedDoc.getDocumentId());
            vectorRepository.saveAll(chunks);
            long storeLatency = System.currentTimeMillis() - tStoreStart;
            response.setStoreLatencyMs(storeLatency);

            long totalLatency = System.currentTimeMillis() - t0;
            response.setTotalLatencyMs(totalLatency);
            response.setSuccess(true);

            log.info("Ingestion completed for '{}': {} chunks, {} pages in {}ms (parse: {}ms, chunk: {}ms, embed: {}ms, store: {}ms)",
                    file.getName(), chunks.size(), parsedDoc.getPages().size(), totalLatency,
                    parseLatency, chunkLatency, embedLatency, storeLatency);

            return response;

        } catch (Exception e) {
            log.error("Failed to ingest document '{}': {}", file.getAbsolutePath(), e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setTotalLatencyMs(System.currentTimeMillis() - t0);
            return response;
        }
    }

    public IndexResponseDto ingest(InputStream inputStream, String fileName, String filePath,
                                  ChunkingStrategy strategy, int targetChunkSize) {
        IndexResponseDto response = new IndexResponseDto();
        response.setFilePath(filePath);
        response.setDocumentName(fileName);

        long t0 = System.currentTimeMillis();

        try {
            long tParseStart = System.currentTimeMillis();
            ParsedDocument parsedDoc = documentParser.parse(inputStream, fileName, filePath);
            long parseLatency = System.currentTimeMillis() - tParseStart;

            response.setDocumentId(parsedDoc.getDocumentId());
            response.setPageCount(parsedDoc.getPages().size());
            response.setParseLatencyMs(parseLatency);

            long tChunkStart = System.currentTimeMillis();
            List<DocumentChunk> chunks = chunkingPipeline.chunk(parsedDoc, strategy, targetChunkSize);
            long chunkLatency = System.currentTimeMillis() - tChunkStart;
            response.setChunkLatencyMs(chunkLatency);
            response.setChunksCount(chunks.size());

            long tEmbedStart = System.currentTimeMillis();
            for (DocumentChunk chunk : chunks) {
                float[] embedding = embeddingService.generateEmbedding(chunk.getContent());
                chunk.setEmbedding(embedding);
            }
            long embedLatency = System.currentTimeMillis() - tEmbedStart;
            response.setEmbedLatencyMs(embedLatency);

            long tStoreStart = System.currentTimeMillis();
            vectorRepository.deleteByDocumentId(parsedDoc.getDocumentId());
            vectorRepository.saveAll(chunks);
            long storeLatency = System.currentTimeMillis() - tStoreStart;
            response.setStoreLatencyMs(storeLatency);

            long totalLatency = System.currentTimeMillis() - t0;
            response.setTotalLatencyMs(totalLatency);
            response.setSuccess(true);

            return response;
        } catch (Exception e) {
            log.error("Failed to ingest stream for '{}': {}", fileName, e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setTotalLatencyMs(System.currentTimeMillis() - t0);
            return response;
        }
    }
}

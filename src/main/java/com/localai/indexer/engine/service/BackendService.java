package com.localai.indexer.engine.service;

import com.localai.indexer.engine.dto.BackendStatusDto;
import com.localai.indexer.engine.dto.IndexRequestDto;
import com.localai.indexer.engine.dto.IndexResponseDto;
import com.localai.indexer.engine.vector.VectorRepository;
import com.localai.indexer.ingestion.IngestionPipeline;
import com.localai.indexer.ingestion.chunking.ChunkingStrategy;
import com.localai.indexer.ingestion.embedding.EmbeddingService;
import com.localai.indexer.search.dto.SearchRequestDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import com.localai.indexer.search.hybrid.HybridSearchEngine;
import com.localai.indexer.search.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;

/**
 * Backend Service Facade.
 * Can be accessed via direct Java method calls from JavaFX frontend
 * or via REST controller endpoints.
 */
@Service
public class BackendService {

    private static final Logger log = LoggerFactory.getLogger(BackendService.class);

    private final IngestionPipeline ingestionPipeline;
    private final HybridSearchEngine hybridSearchEngine;
    private final VectorRepository vectorRepository;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    @Value("${ollama.chat-model:llama3.2}")
    private String chatModel;

    public BackendService(IngestionPipeline ingestionPipeline,
                          HybridSearchEngine hybridSearchEngine,
                          VectorRepository vectorRepository,
                          EmbeddingService embeddingService,
                          LlmService llmService) {
        this.ingestionPipeline = ingestionPipeline;
        this.hybridSearchEngine = hybridSearchEngine;
        this.vectorRepository = vectorRepository;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
    }

    /**
     * Ingest document file by path.
     */
    public IndexResponseDto indexDocument(IndexRequestDto request) {
        if (request == null || request.getFilePath() == null || request.getFilePath().isBlank()) {
            return IndexResponseDto.failure("", "File path is required.");
        }

        File file = new File(request.getFilePath());
        if (!file.exists()) {
            return IndexResponseDto.failure(request.getFilePath(), "File not found: " + request.getFilePath());
        }

        ChunkingStrategy strategy = ChunkingStrategy.SEMANTIC_PARAGRAPH;
        if (request.getStrategy() != null) {
            try {
                strategy = ChunkingStrategy.valueOf(request.getStrategy().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        int targetSize = request.getTargetChunkSize() > 0 ? request.getTargetChunkSize() : 400;

        IndexResponseDto result = ingestionPipeline.ingest(file, strategy, targetSize);
        if (result.isSuccess()) {
            hybridSearchEngine.refreshBM25Index();
        }
        return result;
    }

    /**
     * Ingest document from input stream (e.g. GUI file upload).
     */
    public IndexResponseDto indexStream(InputStream inputStream, String fileName, String filePath,
                                         ChunkingStrategy strategy, int targetSize) {
        IndexResponseDto result = ingestionPipeline.ingest(inputStream, fileName, filePath, strategy, targetSize);
        if (result.isSuccess()) {
            hybridSearchEngine.refreshBM25Index();
        }
        return result;
    }

    /**
     * Query documents using hybrid search.
     */
    public SearchResponseDto search(SearchRequestDto request) {
        return hybridSearchEngine.search(request);
    }

    /**
     * System status for polling.
     */
    public BackendStatusDto getStatus() {
        BackendStatusDto status = new BackendStatusDto();
        status.setStatus("UP");
        status.setIndexedDocumentsCount(vectorRepository.countDistinctDocuments());
        status.setTotalChunksCount(vectorRepository.countChunks());
        status.setOllamaAvailable(llmService.isAvailable());
        status.setOllamaBaseUrl(ollamaBaseUrl);
        status.setEmbeddingModel(embeddingService.getModelName());
        status.setChatModel(llmService.getModelName());
        status.setVectorStorageEngine("H2 Embedded Vector (Zero-Config UDF)");

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        Map<String, Object> mem = new HashMap<>();
        mem.put("usedMb", heap.getUsed() / (1024 * 1024));
        mem.put("maxMb", heap.getMax() / (1024 * 1024));
        status.setMemoryUsage(mem);

        return status;
    }

    public List<Map<String, Object>> listDocuments() {
        return vectorRepository.listIndexedDocuments();
    }

    public List<com.localai.indexer.ingestion.model.DocumentChunk> getDocumentChunks(String documentId) {
        return vectorRepository.findByDocumentId(documentId);
    }

    public boolean deleteDocument(String documentId) {
        int deleted = vectorRepository.deleteByDocumentId(documentId);
        hybridSearchEngine.refreshBM25Index();
        return deleted > 0;
    }

    public void clearAll() {
        vectorRepository.clearAll();
        hybridSearchEngine.refreshBM25Index();
    }
}

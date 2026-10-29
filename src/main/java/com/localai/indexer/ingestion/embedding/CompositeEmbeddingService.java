package com.localai.indexer.ingestion.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite Embedding Service providing seamless failover.
 * Tries local Ollama daemon first; if unreachable, gracefully falls back to
 * the deterministic local semantic embedding engine with explicit logging and telemetry.
 */
@Service
@Primary
public class CompositeEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CompositeEmbeddingService.class);

    private final OllamaEmbeddingService ollamaEmbeddingService;
    private final LocalSemanticEmbeddingService localSemanticEmbeddingService;
    private volatile boolean fallbackActive = false;

    public CompositeEmbeddingService(OllamaEmbeddingService ollamaEmbeddingService,
                                     LocalSemanticEmbeddingService localSemanticEmbeddingService) {
        this.ollamaEmbeddingService = ollamaEmbeddingService;
        this.localSemanticEmbeddingService = localSemanticEmbeddingService;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[getDimension()];
        }

        try {
            float[] ollamaVec = ollamaEmbeddingService.generateEmbedding(text);
            if (ollamaVec != null && ollamaVec.length > 0) {
                if (fallbackActive) {
                    log.info("Ollama embedding service recovered. Using Ollama ({})", ollamaEmbeddingService.getModelName());
                    fallbackActive = false;
                }
                return ollamaVec;
            }
        } catch (Exception e) {
            if (!fallbackActive) {
                log.warn("Ollama embedding service unavailable ({}). Falling back to LocalSemanticEmbeddingService (TF-IDF/Feature-Hash)", e.getMessage());
                fallbackActive = true;
            }
        }

        fallbackActive = true;
        return localSemanticEmbeddingService.generateEmbedding(text);
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> list = new ArrayList<>(texts.size());
        for (String t : texts) {
            list.add(generateEmbedding(t));
        }
        return list;
    }

    @Override
    public int getDimension() {
        if (ollamaEmbeddingService.isAvailable()) {
            return ollamaEmbeddingService.getDimension();
        }
        return localSemanticEmbeddingService.getDimension();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getModelName() {
        if (ollamaEmbeddingService.isAvailable()) {
            return "ollama:" + ollamaEmbeddingService.getModelName();
        }
        return "fallback:" + localSemanticEmbeddingService.getModelName();
    }

    public boolean isFallbackActive() {
        return fallbackActive || !ollamaEmbeddingService.isAvailable();
    }

    public String getActiveServiceName() {
        if (!isFallbackActive()) {
            return "Ollama (" + ollamaEmbeddingService.getModelName() + ")";
        } else {
            return "Local Semantic Fallback (" + localSemanticEmbeddingService.getModelName() + ")";
        }
    }
}

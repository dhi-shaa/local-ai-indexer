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
 * the deterministic local semantic embedding engine with zero interruptions.
 */
@Service
@Primary
public class CompositeEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CompositeEmbeddingService.class);

    private final OllamaEmbeddingService ollamaEmbeddingService;
    private final LocalSemanticEmbeddingService localSemanticEmbeddingService;

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
                return ollamaVec;
            }
        } catch (Exception e) {
            log.trace("Ollama embedding failed, falling back to local dense vector: {}", e.getMessage());
        }

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
        return localSemanticEmbeddingService.getModelName();
    }
}

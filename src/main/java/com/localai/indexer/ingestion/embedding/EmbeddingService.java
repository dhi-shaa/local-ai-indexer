package com.localai.indexer.ingestion.embedding;

import java.util.List;

/**
 * Contract for document text embedding generation.
 */
public interface EmbeddingService {

    /**
     * Compute dense vector embedding for single text snippet.
     */
    float[] generateEmbedding(String text);

    /**
     * Compute embeddings in batch.
     */
    List<float[]> generateEmbeddings(List<String> texts);

    /**
     * Dimension of the generated vectors.
     */
    int getDimension();

    /**
     * True if the underlying embedding provider is reachable.
     */
    boolean isAvailable();

    /**
     * Identifier of the embedding model/engine.
     */
    String getModelName();
}

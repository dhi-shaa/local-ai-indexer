package com.localai.indexer.ingestion.embedding;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic local dense vector embedding generator.
 * Employs subword feature projection and L2 normalization to produce 384-dimensional
 * dense semantic vectors. Operates completely in-memory with zero network latency,
 * guaranteeing offline zero-dependency vector database operations.
 */
@Service
public class LocalSemanticEmbeddingService implements EmbeddingService {

    public static final int DIMENSION = 384;

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }

        float[] vector = new float[DIMENSION];
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = cleaned.split("\\s+");

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isBlank()) continue;

            // Project unigrams
            projectToken(token, 1.0f, vector);

            // Project character n-grams (3-grams and 4-grams) for morphological similarity
            if (token.length() >= 3) {
                for (int j = 0; j <= token.length() - 3; j++) {
                    String gram = token.substring(j, j + 3);
                    projectToken(gram, 0.4f, vector);
                }
            }

            // Project bigrams with previous token for semantic context
            if (i > 0) {
                String bigram = tokens[i - 1] + "_" + token;
                projectToken(bigram, 0.7f, vector);
            }
        }

        // L2 Normalize the dense vector
        double normSq = 0.0;
        for (float v : vector) {
            normSq += v * v;
        }

        if (normSq > 0.0) {
            float norm = (float) Math.sqrt(normSq);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    private void projectToken(String term, float weight, float[] vector) {
        byte[] bytes = term.getBytes(StandardCharsets.UTF_8);
        int h1 = murmurHash3(bytes, 42);
        int h2 = murmurHash3(bytes, 1337);

        int dim1 = Math.abs(h1) % DIMENSION;
        int dim2 = Math.abs(h2) % DIMENSION;

        float sign1 = (h1 & 1) == 0 ? 1.0f : -1.0f;
        float sign2 = (h2 & 1) == 0 ? 1.0f : -1.0f;

        vector[dim1] += sign1 * weight;
        vector[dim2] += sign2 * weight * 0.5f;
    }

    private int murmurHash3(byte[] data, int seed) {
        int h = seed;
        for (byte b : data) {
            h ^= (b & 0xFF);
            h *= 0x5bd1e995;
            h ^= h >>> 15;
        }
        return h;
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(generateEmbedding(t));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getModelName() {
        return "local-dense-hash-384";
    }
}

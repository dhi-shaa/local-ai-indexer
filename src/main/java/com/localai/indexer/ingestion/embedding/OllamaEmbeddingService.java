package com.localai.indexer.ingestion.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Ollama Embedding Client.
 * Connects to local Ollama server (e.g., http://localhost:11434/api/embeddings)
 * for high-dimensional local LLM embeddings (e.g. nomic-embed-text, all-minilm).
 */
@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final String baseUrl;
    private final String modelName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;

    public OllamaEmbeddingService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.embedding-model:nomic-embed-text}") String modelName,
            @Value("${ollama.timeout-seconds:30}") int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(5, timeoutSeconds)))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", modelName);
            payload.put("prompt", text);

            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] vector = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        vector[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return vector;
                }
            } else {
                log.warn("Ollama embedding API returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.debug("Ollama is offline or unreachable: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> list = new ArrayList<>(texts.size());
        for (String text : texts) {
            float[] vec = generateEmbedding(text);
            list.add(vec);
        }
        return list;
    }

    @Override
    public int getDimension() {
        return 768; // Standard dimension for nomic-embed-text
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}

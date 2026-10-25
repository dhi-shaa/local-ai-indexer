package com.localai.indexer.search.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.indexer.search.dto.SearchResultChunkDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Ollama LLM integration for local RAG response generation.
 * Queries local Ollama (e.g. llama3.2 / mistral) with context prompts.
 * Automatically falls back to local contextual extractive synthesis when Ollama is offline.
 */
@Service
public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);

    private final String baseUrl;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;

    public OllamaLlmService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:llama3.2}") String chatModel,
            @Value("${ollama.timeout-seconds:60}") int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.chatModel = chatModel;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(5, timeoutSeconds)))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String synthesizeAnswer(String query, List<SearchResultChunkDto> contextChunks) {
        if (contextChunks == null || contextChunks.isEmpty()) {
            return "No relevant local documents found for query: \"" + query + "\".";
        }

        // Build context prompt with explicit source and page citations
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an assistant answering queries strictly using the provided local private document context.\n");
        prompt.append("CONTEXT:\n");
        for (SearchResultChunkDto chunk : contextChunks) {
            prompt.append(String.format("[Document: %s | Page: %d | Chunk: %d]\n%s\n\n",
                    chunk.getDocumentName(), chunk.getPageNumber(), chunk.getChunkIndex(), chunk.getContent()));
        }
        prompt.append("QUESTION: ").append(query).append("\n");
        prompt.append("INSTRUCTIONS: Provide a clear, factual answer synthesized from the context. Always cite document name and page number.\n");

        // Try local Ollama generation first
        if (isAvailable()) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", chatModel);
                body.put("prompt", prompt.toString());
                body.put("stream", false);

                String requestJson = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode node = objectMapper.readTree(response.body());
                    if (node.has("response")) {
                        return node.get("response").asText().trim();
                    }
                }
            } catch (Exception e) {
                log.warn("Ollama synthesis failed: {}. Falling back to local extractive synthesis.", e.getMessage());
            }
        }

        // Resilient fallback: Extractive local synthesis from top chunks
        return fallbackLocalSynthesis(query, contextChunks);
    }

    private String fallbackLocalSynthesis(String query, List<SearchResultChunkDto> chunks) {
        SearchResultChunkDto topChunk = chunks.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Based on the indexed document ").append(topChunk.getDocumentName());
        sb.append(" (Page ").append(topChunk.getPageNumber()).append("):\n\n");

        // Select the most informative passage
        String content = topChunk.getContent().trim();
        sb.append(content);

        if (chunks.size() > 1) {
            sb.append("\n\nAdditional supporting evidence found in ");
            Set<String> extraRefs = new LinkedHashSet<>();
            for (int i = 1; i < chunks.size(); i++) {
                SearchResultChunkDto c = chunks.get(i);
                extraRefs.add(String.format("%s (Page %d)", c.getDocumentName(), c.getPageNumber()));
            }
            sb.append(String.join(", ", extraRefs)).append(".");
        }

        return sb.toString();
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
        return isAvailable() ? ("ollama:" + chatModel) : "local-synthesis-engine";
    }
}

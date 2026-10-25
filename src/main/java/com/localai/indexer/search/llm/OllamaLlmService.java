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
            @Value("${ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${ollama.chat-model:phi3}") String chatModel,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.chatModel = chatModel;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(120))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String synthesizeAnswer(String query, List<SearchResultChunkDto> contextChunks) {
        if (contextChunks == null || contextChunks.isEmpty()) {
            return "No relevant local documents found for query: \"" + query + "\".";
        }

        // Construct {retrieved_chunks} from top retrieved document chunk excerpts
        StringBuilder retrievedChunks = new StringBuilder();
        for (int i = 0; i < contextChunks.size(); i++) {
            SearchResultChunkDto chunk = contextChunks.get(i);
            retrievedChunks.append(String.format("[%d] %s (Page %d):\n%s\n\n",
                    i + 1, chunk.getDocumentName(), chunk.getPageNumber(), chunk.getContent()));
        }

        // Construct exact RAG synthesis prompt
        String prompt = "You are an offline assistant. Using ONLY the document excerpts provided below, answer the user's question clearly and concisely.\n" +
                "Context:\n" +
                retrievedChunks.toString().trim() + "\n\n" +
                "User Question: " + query;

        // Connect to local Ollama on loopback: http://127.0.0.1:11434/api/generate (model: phi3)
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", chatModel);
            body.put("prompt", prompt);
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
                    String answer = node.get("response").asText().trim();
                    if (!answer.isEmpty()) {
                        return answer;
                    }
                }
            }
            return "Ollama is not running locally. Please ensure Ollama is active on port 11434.";
        } catch (Exception e) {
            log.warn("Ollama is unreachable on {}: {}", baseUrl, e.getMessage());
            return "Ollama is not running locally. Please ensure Ollama is active on port 11434.";
        }
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

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
 * Queries local Ollama with context prompts, explicit num_ctx, and table row alignment rules.
 * Automatically falls back to informative local offline messages when Ollama is offline.
 */
@Service
public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);

    private final String baseUrl;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;
    private final int numCtx;
    private final double temperature;
    private final boolean debugPrompt;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaLlmService(
            @Value("${ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${ollama.chat-model:phi3}") String chatModel,
            @Value("${ollama.timeout-seconds:120}") int timeoutSeconds,
            @Value("${ollama.num-ctx:4096}") int numCtx,
            @Value("${ollama.temperature:0.2}") double temperature,
            @Value("${ollama.debug-prompt:true}") boolean debugPrompt) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.chatModel = chatModel;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        this.numCtx = numCtx > 0 ? numCtx : 4096;
        this.temperature = temperature;
        this.debugPrompt = debugPrompt;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(120))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public OllamaLlmService(String baseUrl, String chatModel, int timeoutSeconds) {
        this(baseUrl, chatModel, timeoutSeconds, 4096, 0.2, true);
    }

    @Override
    public String synthesizeAnswer(String query, List<SearchResultChunkDto> contextChunks) {
        if (contextChunks == null || contextChunks.isEmpty()) {
            return "The provided local documents do not contain information to answer this question.";
        }

        // Construct {retrieved_chunks} from top retrieved document chunk excerpts
        StringBuilder retrievedChunks = new StringBuilder();
        for (int i = 0; i < contextChunks.size(); i++) {
            SearchResultChunkDto chunk = contextChunks.get(i);
            String chunkText = chunk.getText() != null && !chunk.getText().isBlank()
                    ? chunk.getText()
                    : (chunk.getContent() != null ? chunk.getContent() : "");
            String docLabel = chunk.getDocumentName() != null && !chunk.getDocumentName().isBlank()
                    ? chunk.getDocumentName()
                    : (chunk.getFilePath() != null ? chunk.getFilePath() : "Document");
            String pageInfo = chunk.getPageLabel() != null ? chunk.getPageLabel() : ("Page " + chunk.getPageNumber());

            retrievedChunks.append(String.format("[%d] %s (%s):\n%s\n\n",
                    i + 1, docLabel, pageInfo, chunkText.trim()));
        }

        // Construct exact RAG synthesis prompt with table pair instruction
        String prompt = "You are an offline assistant. Using ONLY the document excerpts provided below, answer the user's question clearly and concisely.\n\n" +
                "IMPORTANT INSTRUCTIONS:\n" +
                "1. Base your answer strictly on the provided context excerpts.\n" +
                "2. When reading tabular data, lists of threats and mitigations, or comparison columns, pair items strictly by row (e.g. match each Threat or Limitation directly to its corresponding Mitigation as presented in the excerpt). Never mix or swap mitigations across different threats. If a row's pairing is ambiguous, state that explicitly.\n" +
                "3. If the excerpts do not contain enough information to answer the question, state that the indexed documents do not contain this information. Do not fabricate details.\n\n" +
                "Context:\n" +
                retrievedChunks.toString().trim() + "\n\n" +
                "User Question: " + query + "\n\n" +
                "Answer:";

        if (debugPrompt) {
            log.info("=== RAG PROMPT SENT TO OLLAMA (Model: {}, num_ctx: {}, Prompt Length: {}) ===\n{}\n==========================================================================",
                    chatModel, numCtx, prompt.length(), prompt);
            System.out.println("=== RAG PROMPT CONTEXT SENT TO OLLAMA ===");
            System.out.println(prompt);
            System.out.println("=========================================");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", chatModel);
            body.put("prompt", prompt);
            body.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            options.put("num_ctx", numCtx);
            options.put("temperature", temperature);
            body.put("options", options);

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
            return "Ollama returned status " + response.statusCode() + ". Please verify model availability.";
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

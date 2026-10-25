package com.localai.indexer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.indexer.model.DocumentChunkDto;
import com.localai.indexer.model.HybridSearchResultDto;
import com.localai.indexer.model.PipelineMetricsDto;
import com.localai.indexer.model.PipelineStageTiming;
import com.localai.indexer.model.SourceCitation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Service acting as the backend engine connector. Produces pipeline stage metrics,
 * responds to hybrid search queries with citations, and generates high-dimensional embeddings.
 */
public class MockPipelineBackendService {

    private static final MockPipelineBackendService INSTANCE = new MockPipelineBackendService();
    private final Random random = new Random(42);
    private final List<Consumer<PipelineMetricsDto>> metricsListeners = new CopyOnWriteArrayList<>();
    private final List<DocumentChunkDto> indexedChunks = new ArrayList<>();
    private int customIndexedCount = 0;

    public static MockPipelineBackendService getInstance() {
        return INSTANCE;
    }

    public MockPipelineBackendService() {
        seedSampleData();
    }

    public void addMetricsListener(Consumer<PipelineMetricsDto> listener) {
        metricsListeners.add(listener);
    }

    public void removeMetricsListener(Consumer<PipelineMetricsDto> listener) {
        metricsListeners.remove(listener);
    }

    /**
     * Produces a single live pipeline metrics frame.
     */
    public PipelineMetricsDto generateMetricsFrame(int step) {
        String[] sampleFiles = {
            "docs/architecture/ADR-001-hybrid-search.md",
            "docs/privacy/local-airgap-policy.pdf",
            "src/core/nlp/RecursiveTokenChunker.java",
            "specs/vector-h2-engine-spec.pdf",
            "configs/offline-embedding-onnx.json"
        };
        String currentFile = sampleFiles[step % sampleFiles.length];

        // Per-stage timings (parse, chunk, embed, store)
        long parseMs = 15 + random.nextInt(25);
        long chunkMs = 8 + random.nextInt(15);
        long embedMs = 45 + random.nextInt(60); // Embedding execution latency
        long storeMs = 12 + random.nextInt(18);

        PipelineStageTiming timing = new PipelineStageTiming(parseMs, chunkMs, embedMs, storeMs);
        double throughputDocsPerSec = 14.5 + (random.nextDouble() * 8.0);
        double embeddingLatencyMs = embedMs + (random.nextDouble() * 5.0);
        int activeDocs = 12 + (step % 20);
        int totalIndexed = 250 + (step * 8) + customIndexedCount;
        double memMb = 142.0 + (random.nextDouble() * 20.0);

        PipelineMetricsDto metrics = new PipelineMetricsDto(
            throughputDocsPerSec,
            embeddingLatencyMs,
            timing,
            activeDocs,
            totalIndexed,
            memMb,
            currentFile
        );

        for (Consumer<PipelineMetricsDto> listener : metricsListeners) {
            try {
                listener.accept(metrics);
            } catch (Exception ignored) {}
        }

        return metrics;
    }

    /**
     * Executes hybrid search and produces answer with ranked source citations.
     */
    public HybridSearchResultDto executeSearch(String query) {
        long startTime = System.currentTimeMillis();
        String qLower = query.toLowerCase();

        // Check if full Spring Boot HybridSearchEngine is active in current context
        try {
            if (com.localai.indexer.engine.LocalAiIndexerApplication.getContext() != null &&
                com.localai.indexer.engine.LocalAiIndexerApplication.getContext().isActive()) {
                com.localai.indexer.search.hybrid.HybridSearchEngine engine =
                    com.localai.indexer.engine.LocalAiIndexerApplication.getBean(com.localai.indexer.search.hybrid.HybridSearchEngine.class);
                if (engine != null) {
                    com.localai.indexer.search.dto.SearchRequestDto req =
                        new com.localai.indexer.search.dto.SearchRequestDto(query, 6, 0.5);
                    req.setSynthesizeResponse(true);
                    req.setSimilarityMetric("cosine");
                    com.localai.indexer.search.dto.SearchResponseDto resp = engine.search(req);
                    if (resp != null && resp.getChunks() != null && !resp.getChunks().isEmpty()) {
                        List<SourceCitation> resCitations = new ArrayList<>();
                        for (com.localai.indexer.search.dto.SearchResultChunkDto chunk : resp.getChunks()) {
                            String docName = chunk.getDocumentName() != null ? chunk.getDocumentName() : chunk.getFilePath();
                            resCitations.add(new SourceCitation(
                                chunk.getChunkId(),
                                docName,
                                chunk.getPageNumber(),
                                chunk.getHybridScore(),
                                "Section from " + docName,
                                chunk.getText()
                            ));
                        }
                        long latency = System.currentTimeMillis() - startTime;
                        return new HybridSearchResultDto(query, resp.getAiResponse(), latency, resCitations);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Standalone In-Process Matching with User-Document Priority Boosting
        List<SourceCitation> citations = new ArrayList<>();
        List<SourceCitation> userMatchedCitations = new ArrayList<>();
        List<SourceCitation> systemMatchedCitations = new ArrayList<>();

        String[] queryWords = qLower.split("\\s+");
        Set<String> searchTerms = new HashSet<>();
        for (String w : queryWords) {
            String clean = w.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (clean.length() >= 3) {
                searchTerms.add(clean);
            }
        }

        // Scan all indexed chunks (user documents and seeded knowledge base)
        for (DocumentChunkDto chunk : indexedChunks) {
            String text = chunk.textSnippet() != null ? chunk.textSnippet().toLowerCase() : "";
            String name = chunk.fileName() != null ? chunk.fileName().toLowerCase() : "";
            boolean isUserDoc = !chunk.chunkId().startsWith("chk_0") &&
                !name.contains("docs/architecture") && !name.contains("docs/privacy") && !name.contains("specs/");

            int matches = 0;
            for (String term : searchTerms) {
                if (text.contains(term)) matches += 2;
                if (name.contains(term)) matches += 4;
            }

            if (matches > 0) {
                double score = 0.82 + Math.min(0.16, matches * 0.03);
                if (isUserDoc) {
                    score += 0.25; // Priority boost for user-uploaded documents over internal system docs
                    userMatchedCitations.add(new SourceCitation(
                        chunk.chunkId(),
                        chunk.fileName(),
                        chunk.pageNumber(),
                        score,
                        "Matched section from " + chunk.fileName(),
                        chunk.textSnippet()
                    ));
                } else {
                    systemMatchedCitations.add(new SourceCitation(
                        chunk.chunkId(),
                        chunk.fileName(),
                        chunk.pageNumber(),
                        score,
                        "Matched section from " + chunk.fileName(),
                        chunk.textSnippet()
                    ));
                }
            }
        }

        userMatchedCitations.sort((a, b) -> Double.compare(b.score(), a.score()));
        systemMatchedCitations.sort((a, b) -> Double.compare(b.score(), a.score()));

        // Prioritize user document citations first
        citations.addAll(userMatchedCitations);

        // Fill remaining citations with matching system citations if under 6
        if (citations.size() < 6) {
            for (SourceCitation sc : systemMatchedCitations) {
                if (citations.stream().noneMatch(c -> c.chunkId().equals(sc.chunkId()))) {
                    citations.add(sc);
                    if (citations.size() >= 6) break;
                }
            }
        }

        // Backfill with category defaults if still under 6
        if (citations.size() < 6) {
            List<SourceCitation> fallbackList = getFallbackCitations(qLower);
            for (SourceCitation sc : fallbackList) {
                if (citations.stream().noneMatch(c -> c.chunkId().equals(sc.chunkId()))) {
                    citations.add(sc);
                    if (citations.size() >= 6) break;
                }
            }
        }

        // Limit to top 6 citations for synthesis
        List<SourceCitation> top6Citations = citations.subList(0, Math.min(6, citations.size()));
        String answer = synthesizeWithLocalOllama(query, top6Citations);

        long latency = System.currentTimeMillis() - startTime + 38 + random.nextInt(20);
        return new HybridSearchResultDto(query, answer, latency, citations);
    }

    private List<SourceCitation> getFallbackCitations(String qLower) {
        List<SourceCitation> citations = new ArrayList<>();
        if (qLower.contains("privacy") || qLower.contains("network") || qLower.contains("offline") || qLower.contains("airgap")) {
            citations.add(new SourceCitation(
                "chk_priv_01",
                "docs/privacy/local-airgap-policy.pdf",
                1,
                0.97,
                "Section 1.1: Air-Gap Architecture",
                "Strict local containment: The indexing runtime binds exclusively to loopback (127.0.0.1). Any outbound internet socket connection attempts are intercepted and blocked at the JVM level."
            ));
            citations.add(new SourceCitation(
                "chk_priv_02",
                "docs/privacy/local-airgap-policy.pdf",
                3,
                0.93,
                "Section 2.4: Zero Telemetry Guarantee",
                "External HTTP/HTTPS and DNS telemetry requests are permanently disabled. User document repositories and vector embeddings never exit the local host machine."
            ));
            citations.add(new SourceCitation(
                "chk_priv_03",
                "src/main/java/com/localai/indexer/service/LocalPrivacyService.java",
                1,
                0.89,
                "Class: LocalPrivacyService",
                "Monitors network sockets, confirms active interfaces, and maintains cryptographic audit proofs verifying zero outbound network transmission."
            ));
            citations.add(new SourceCitation(
                "chk_priv_04",
                "docs/privacy/security-audit-v1.pdf",
                2,
                0.86,
                "Audit Verification",
                "Cryptographic checksums verify zero outbound transmission across all active physical network interfaces."
            ));
            citations.add(new SourceCitation(
                "chk_priv_05",
                "docs/privacy/local-airgap-policy.pdf",
                2,
                0.84,
                "Socket Interception",
                "Security manager blocks external host resolution, enforcing localhost loopback constraints."
            ));
            citations.add(new SourceCitation(
                "chk_priv_06",
                "docs/architecture/ADR-001-hybrid-search.md",
                1,
                0.81,
                "Local Query Pipeline",
                "All vector similarity computations and BM25 queries execute strictly inside the workstation memory."
            ));
        } else if (qLower.contains("embed") || qLower.contains("pca") || qLower.contains("vector") || qLower.contains("dimension")) {
            citations.add(new SourceCitation(
                "chk_vec_01",
                "specs/vector-h2-engine-spec.pdf",
                2,
                0.96,
                "Section 3: Dimensionality & Persistence",
                "Document chunks are transformed into 384-dimensional dense vectors. Persistent storage leverages H2 vector indexes with cosine distance similarity metrics."
            ));
            citations.add(new SourceCitation(
                "chk_vec_02",
                "docs/architecture/ADR-004-pca-reduction.md",
                1,
                0.92,
                "Principal Component Reduction Design",
                "Interactive visual analysis reduces high-dimensional vector embeddings to 2D coordinates using covariance matrix decomposition and power iteration without external native math libraries."
            ));
            citations.add(new SourceCitation(
                "chk_vec_03",
                "specs/h2-vector-schema-p1.sql",
                1,
                0.89,
                "H2 Vector Table Schema",
                "Custom SQL ALIAS functions COSINE_SIMILARITY and DOT_PRODUCT calculate similarity directly in H2 SQL queries."
            ));
            citations.add(new SourceCitation(
                "chk_vec_04",
                "src/ui/visualize/CanvasRenderer-1.java",
                2,
                0.86,
                "PCA Visualization Canvas",
                "Scatter plot renders PCA coordinates dynamically with zoom, pan, and cluster color palettes."
            ));
            citations.add(new SourceCitation(
                "chk_vec_05",
                "src/core/nlp/TokenEmbedder-1.java",
                3,
                0.84,
                "Local Token Embedding",
                "Dense vector generation runs purely in-process via ONNX or Ollama with zero external telemetry."
            ));
            citations.add(new SourceCitation(
                "chk_vec_06",
                "docs/architecture/ADR-001-hybrid-search.md",
                1,
                0.82,
                "Dense Retrieval Fusion",
                "Normalized vector cosine similarity fuses with BM25 inverted index for robust recall."
            ));
        } else {
            citations.add(new SourceCitation(
                "chk_hyb_01",
                "docs/architecture/ADR-001-hybrid-search.md",
                1,
                0.95,
                "Architecture Decision: Hybrid Retrieval",
                "Combining dense semantic embeddings with BM25 keyword inverted index yields optimal recall for domain-specific technical documentation and source code identifiers."
            ));
            citations.add(new SourceCitation(
                "chk_hyb_02",
                "src/core/nlp/RecursiveTokenChunker.java",
                2,
                0.90,
                "Recursive Chunking Strategy",
                "Documents parsed by Apache Tika are chunked into 512-token segments with 64-token overlap, preserving heading hierarchies, file paths, and exact page numbers."
            ));
            citations.add(new SourceCitation(
                "chk_hyb_03",
                "specs/pipeline-spec.pdf",
                4,
                0.86,
                "Pipeline Ingestion Benchmarks",
                "Multi-threaded pipeline achieves >15 docs/sec ingestion throughput with per-stage telemetry tracking parse, chunk, embed, and store phases."
            ));
            citations.add(new SourceCitation(
                "chk_hyb_04",
                "specs/vector-h2-engine-spec.pdf",
                2,
                0.84,
                "H2 Embedded Vector Store",
                "Embedded H2 vector tables calculate cosine distance directly using native in-engine SQL ALIAS functions without cloud dependencies."
            ));
            citations.add(new SourceCitation(
                "chk_hyb_05",
                "docs/privacy/local-airgap-policy.pdf",
                2,
                0.82,
                "Air-Gap Verification Protocol",
                "Strict local containment guarantees all inferences and search queries execute on localhost loopback with zero external telemetry."
            ));
            citations.add(new SourceCitation(
                "chk_hyb_06",
                "docs/architecture/ADR-004-pca-reduction.md",
                1,
                0.80,
                "PCA Dimensionality Mapping",
                "Covariance matrix power iteration projects high-dimensional vectors to 2D scatter coordinates for visual space exploration."
            ));
        }
        return citations;
    }

    private String synthesizeWithLocalOllama(String query, List<SourceCitation> citations) {
        StringBuilder retrievedChunks = new StringBuilder();
        int limit = Math.min(6, citations.size());
        for (int i = 0; i < limit; i++) {
            SourceCitation c = citations.get(i);
            retrievedChunks.append(String.format("[%d] %s (Page %d):\n%s\n\n",
                i + 1, c.fileName(), c.pageNumber(), c.textSnippet()));
        }

        String prompt = "You are an offline assistant. Using ONLY the document excerpts provided below, answer the user's question clearly and concisely.\n" +
            "Context:\n" +
            retrievedChunks.toString().trim() + "\n\n" +
            "User Question: " + query;

        System.out.println("=== RAG PROMPT CONTEXT SENT TO OLLAMA ===");
        System.out.println(prompt);
        System.out.println("=========================================");

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(120))
                .build();

            Map<String, Object> body = new HashMap<>();
            body.put("model", "phi3");
            body.put("prompt", prompt);
            body.put("stream", false);

            ObjectMapper mapper = new ObjectMapper();
            String requestJson = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:11434/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = mapper.readTree(response.body());
                if (node.has("response")) {
                    String ans = node.get("response").asText().trim();
                    if (!ans.isEmpty()) {
                        return ans;
                    }
                }
            }
            return "Ollama is not running locally. Please ensure Ollama is active on port 11434.";
        } catch (Exception e) {
            return "Ollama is not running locally. Please ensure Ollama is active on port 11434.";
        }
    }

    /**
     * Seeds realistic clusters of document chunks with 32-dimensional synthetic embeddings
     * grouped by topic (Architecture, Privacy/Security, Database/Storage, UI/Visualization, NLP/Embeddings).
     */
    private void seedSampleData() {
        String[] categories = {"Architecture", "Privacy/Security", "Database/Storage", "UI/Visualization", "NLP/Embeddings"};
        int dimensions = 32;

        // Cluster centers for realistic PCA separation
        double[][] centers = new double[categories.length][dimensions];
        for (int c = 0; c < categories.length; c++) {
            for (int d = 0; d < dimensions; d++) {
                centers[c][d] = (c == d % categories.length) ? 2.5 : (random.nextDouble() * 0.4 - 0.2);
            }
        }

        int chunkCount = 120;
        for (int i = 0; i < chunkCount; i++) {
            int catIdx = i % categories.length;
            String category = categories[catIdx];
            String chunkId = String.format("chk_%04d", i + 1);
            String fileName = switch (catIdx) {
                case 0 -> "docs/architecture/ADR-" + (i % 5 + 1) + ".md";
                case 1 -> "docs/privacy/security-audit-v" + (i % 3 + 1) + ".pdf";
                case 2 -> "specs/h2-vector-schema-p" + (i % 4 + 1) + ".sql";
                case 3 -> "src/ui/visualize/CanvasRenderer-" + (i % 3 + 1) + ".java";
                default -> "src/core/nlp/TokenEmbedder-" + (i % 4 + 1) + ".java";
            };
            int page = (i % 8) + 1;
            String snippet = "Document excerpt for [" + chunkId + "] in " + category + " from " + fileName +
                " page " + page + ". Contains contextual embeddings, semantic boundaries, and vector representations.";

            // Generate vector near cluster center with random perturbation
            double[] vector = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                vector[d] = centers[catIdx][d] + (random.nextGaussian() * 0.35);
            }

            indexedChunks.add(new DocumentChunkDto(chunkId, fileName, page, category, snippet, vector, 0.0, 0.0));
        }
    }

    public synchronized void registerIngestedDocument(String fileName, int chunkCount, List<DocumentChunkDto> newChunks, PipelineStageTiming timing) {
        if (newChunks != null && !newChunks.isEmpty()) {
            indexedChunks.addAll(newChunks);
        }
        this.customIndexedCount += chunkCount;

        if (timing != null) {
            PipelineMetricsDto frame = new PipelineMetricsDto(
                18.0,
                timing.embedLatencyMs(),
                timing,
                1,
                250 + customIndexedCount,
                156.0,
                fileName
            );
            for (Consumer<PipelineMetricsDto> listener : metricsListeners) {
                try {
                    listener.accept(frame);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized List<DocumentChunkDto> getIndexedChunks() {
        return new ArrayList<>(indexedChunks);
    }
}

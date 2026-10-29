package com.localai.indexer.search.hybrid;

import com.localai.indexer.engine.vector.VectorRepository;
import com.localai.indexer.ingestion.embedding.EmbeddingService;
import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.search.bm25.BM25Engine;
import com.localai.indexer.search.dto.SearchRequestDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import com.localai.indexer.search.dto.SearchResultChunkDto;
import com.localai.indexer.search.llm.LlmService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Hybrid Search Engine.
 * Integrates Okapi BM25 keyword search with H2 native vector cosine similarity,
 * normalizes scores with alpha weighting, filters by relevance threshold,
 * caps LLM context chunks, and produces unified RAG responses.
 */
@Service
public class HybridSearchEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchEngine.class);

    private final VectorRepository vectorRepository;
    private final BM25Engine bm25Engine;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;

    @Value("${indexer.search.min-score-threshold:0.35}")
    private double minScoreThreshold;

    @Value("${indexer.search.max-llm-chunks:5}")
    private int maxLlmChunks;

    @Value("${indexer.search.enable-query-expansion:true}")
    private boolean enableQueryExpansion;

    @Value("${indexer.search.exclude-references:false}")
    private boolean excludeReferences;

    @Value("${indexer.search.reference-weight-multiplier:0.3}")
    private double referenceWeightMultiplier;

    @Value("${indexer.search.include-internal-docs:false}")
    private boolean includeInternalDocs;

    public HybridSearchEngine(VectorRepository vectorRepository,
                              BM25Engine bm25Engine,
                              EmbeddingService embeddingService,
                              LlmService llmService) {
        this.vectorRepository = vectorRepository;
        this.bm25Engine = bm25Engine;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
    }

    @PostConstruct
    public void warmUpIndex() {
        refreshBM25Index();
    }

    public synchronized void refreshBM25Index() {
        List<DocumentChunk> allChunks = vectorRepository.findAll();
        bm25Engine.clear();
        bm25Engine.indexChunks(allChunks);
        log.info("BM25 Inverted Index synchronized with {} chunks from H2.", allChunks.size());
    }

    public synchronized void indexDocumentChunks(List<DocumentChunk> chunks) {
        if (chunks != null && !chunks.isEmpty()) {
            bm25Engine.indexChunks(chunks);
            log.info("Dynamically indexed {} new document chunks into BM25.", chunks.size());
        }
    }

    /**
     * Execute hybrid search combining BM25 keyword matching and H2 vector similarity.
     */
    public SearchResponseDto search(SearchRequestDto request) {
        String query = request.getQuery();
        int requestedTopK = Math.max(1, request.getTopK());
        int topK = Math.min(requestedTopK, maxLlmChunks);
        double alpha = Math.max(0.0, Math.min(1.0, request.getAlpha()));
        String metric = request.getSimilarityMetric() != null ? request.getSimilarityMetric() : "cosine";
        double threshold = request.getMinScore() > 0 ? request.getMinScore() : minScoreThreshold;

        long tSearchStart = System.currentTimeMillis();

        // Log active embedding service
        log.info("Hybrid Search query '{}' handled by embedding service: {}", query, embeddingService.getModelName());

        // 1. Vector Search via H2 Embedded Vector DB
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        int poolSize = Math.max(topK * 6, 30);

        List<VectorRepository.VectorSearchResult> vectorResults =
                vectorRepository.searchSimilar(queryEmbedding, poolSize, metric);

        Map<String, Double> vectorScoreMap = new HashMap<>();
        Map<String, DocumentChunk> chunkLookup = new HashMap<>();

        for (VectorRepository.VectorSearchResult vr : vectorResults) {
            vectorScoreMap.put(vr.getChunk().getId(), vr.getScore());
            chunkLookup.put(vr.getChunk().getId(), vr.getChunk());
        }

        // 2. BM25 Keyword Search with Query Expansion
        String bm25Query = query;
        if (enableQueryExpansion) {
            bm25Query = expandQuery(query);
            log.debug("BM25 expanded query: '{}' -> '{}'", query, bm25Query);
        }

        List<BM25Engine.BM25ScoredResult> bm25Results = bm25Engine.search(bm25Query, poolSize);
        Map<String, Double> bm25ScoreMap = new HashMap<>();

        for (BM25Engine.BM25ScoredResult br : bm25Results) {
            bm25ScoreMap.put(br.getChunk().getId(), br.getScore());
            chunkLookup.putIfAbsent(br.getChunk().getId(), br.getChunk());
        }

        // 3. Score Normalization & Hybrid Fusion
        double maxBm25 = bm25Results.stream().mapToDouble(BM25Engine.BM25ScoredResult::getScore).max().orElse(1.0);
        if (maxBm25 <= 0.0) maxBm25 = 1.0;

        Set<String> allCandidateIds = new HashSet<>(chunkLookup.keySet());
        List<SearchResultChunkDto> scoredCandidates = new ArrayList<>();

        for (String id : allCandidateIds) {
            DocumentChunk chunk = chunkLookup.get(id);
            if (chunk == null) continue;

            String path = chunk.getFilePath() != null ? chunk.getFilePath().toLowerCase() : "";
            boolean isInternalSystemDoc = path.contains("docs/architecture") || path.contains("docs\\architecture")
                    || path.contains("docs/privacy") || path.contains("docs\\privacy")
                    || path.startsWith("specs/") || path.startsWith("specs\\");

            // Drop un-uploaded internal mock/spec docs unless explicitly enabled
            if (isInternalSystemDoc && !includeInternalDocs) {
                continue;
            }

            double rawVector = vectorScoreMap.getOrDefault(id, 0.0);
            // Cosine similarity normalization: [-1, 1] -> [0, 1]
            double normVector = Math.max(0.0, Math.min(1.0, (rawVector + 1.0) / 2.0));

            double rawBm25 = bm25ScoreMap.getOrDefault(id, 0.0);
            double normBm25 = Math.max(0.0, rawBm25 / maxBm25);

            double hybridScore = (alpha * normVector) + ((1.0 - alpha) * normBm25);

            // References / Bibliography handling
            boolean isRef = isReferenceChunk(chunk);
            if (isRef) {
                if (excludeReferences) {
                    continue; // Skip bibliography chunks
                }
                hybridScore *= referenceWeightMultiplier;
            }

            // Drop weak matches below relevance threshold
            if (hybridScore < threshold) {
                continue;
            }

            SearchResultChunkDto dto = new SearchResultChunkDto(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getFilePath(),
                    chunk.getDocumentName(),
                    chunk.getStartPageNumber(),
                    chunk.getEndPageNumber(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    rawVector,
                    rawBm25,
                    hybridScore
            );
            scoredCandidates.add(dto);
        }

        // Sort descending by hybrid score
        scoredCandidates.sort((a, b) -> Double.compare(b.getHybridScore(), a.getHybridScore()));

        // Cap to topK (at most maxLlmChunks)
        List<SearchResultChunkDto> topChunks = scoredCandidates.size() > topK
                ? scoredCandidates.subList(0, topK)
                : scoredCandidates;

        // Log detailed per-chunk scores
        log.info("=== HYBRID SEARCH RESULTS (Query: '{}', Candidates: {}, Top: {}) ===", query, scoredCandidates.size(), topChunks.size());
        for (int i = 0; i < topChunks.size(); i++) {
            SearchResultChunkDto c = topChunks.get(i);
            log.info("  [{}] Chunk: {}, Document: {}, Page: {}, Hybrid: {}, BM25: (raw={}, norm={}), Vector: (raw={}, norm={})",
                    i + 1, c.getChunkId(), c.getDocumentName(), c.getPageLabel(),
                    String.format("%.4f", c.getHybridScore()),
                    String.format("%.4f", c.getBm25Score()),
                    String.format("%.4f", c.getBm25Score() / maxBm25),
                    String.format("%.4f", c.getVectorScore()),
                    String.format("%.4f", (c.getVectorScore() + 1.0) / 2.0));
        }

        long searchLatency = System.currentTimeMillis() - tSearchStart;

        // 4. Source tracking (paths & pages)
        Set<String> distinctSources = new LinkedHashSet<>();
        Set<Integer> distinctPages = new TreeSet<>();
        for (SearchResultChunkDto c : topChunks) {
            if (c.getFilePath() != null) distinctSources.add(c.getFilePath());
            distinctPages.add(c.getPageNumber());
        }

        // 5. LLM Synthesis
        long tSynthStart = System.currentTimeMillis();
        String synthesizedResponse = "";
        if (request.isSynthesizeResponse()) {
            synthesizedResponse = llmService.synthesizeAnswer(query, topChunks);
        }
        long synthesisLatency = System.currentTimeMillis() - tSynthStart;

        SearchResponseDto responseDto = new SearchResponseDto(
                query,
                synthesizedResponse,
                topChunks,
                new ArrayList<>(distinctSources),
                new ArrayList<>(distinctPages),
                searchLatency,
                synthesisLatency,
                llmService.getModelName()
        );
        responseDto.setAiResponse(synthesizedResponse);
        return responseDto;
    }

    private String expandQuery(String query) {
        String lower = query.toLowerCase();
        StringBuilder expanded = new StringBuilder(query);

        if (lower.contains("threat") || lower.contains("limitation")) {
            expanded.append(" attack vulnerability mitigation weakness");
        }
        if (lower.contains("kerberos")) {
            expanded.append(" kdc as-rep roasting kerberoasting ticket krbtgt");
        }
        if (lower.contains("entropy") || lower.contains("id3")) {
            expanded.append(" information gain decision tree split node attribute");
        }
        if (lower.contains("logistic")) {
            expanded.append(" sigmoid gradient descent binary classification loss");
        }
        return expanded.toString();
    }

    private boolean isReferenceChunk(DocumentChunk chunk) {
        if (chunk.getMetadata() != null && Boolean.TRUE.equals(chunk.getMetadata().get("isReference"))) {
            return true;
        }
        String text = chunk.getContent() != null ? chunk.getContent().toLowerCase() : "";
        return text.startsWith("[slide: references") || text.startsWith("[slide: bibliography") ||
               text.contains("rfc 4120") && text.contains("neuman");
    }
}

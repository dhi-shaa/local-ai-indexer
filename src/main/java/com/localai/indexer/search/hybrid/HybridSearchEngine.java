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
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Hybrid Search Engine.
 * Integrates from-scratch Okapi BM25 keyword search with H2 native vector cosine similarity,
 * normalizes scores with alpha weighting, and produces unified RAG responses.
 */
@Service
public class HybridSearchEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchEngine.class);

    private final VectorRepository vectorRepository;
    private final BM25Engine bm25Engine;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;

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

    /**
     * Execute hybrid search combining BM25 keyword matching and H2 vector similarity.
     */
    public SearchResponseDto search(SearchRequestDto request) {
        String query = request.getQuery();
        int topK = Math.max(1, request.getTopK());
        double alpha = Math.max(0.0, Math.min(1.0, request.getAlpha()));
        String metric = request.getSimilarityMetric() != null ? request.getSimilarityMetric() : "cosine";

        long tSearchStart = System.currentTimeMillis();

        // 1. Vector Search via H2 Embedded Vector DB
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        int poolSize = Math.max(topK * 4, 25);

        List<VectorRepository.VectorSearchResult> vectorResults =
                vectorRepository.searchSimilar(queryEmbedding, poolSize, metric);

        Map<String, Double> vectorScoreMap = new HashMap<>();
        Map<String, DocumentChunk> chunkLookup = new HashMap<>();

        for (VectorRepository.VectorSearchResult vr : vectorResults) {
            vectorScoreMap.put(vr.getChunk().getId(), vr.getScore());
            chunkLookup.put(vr.getChunk().getId(), vr.getChunk());
        }

        // 2. BM25 Keyword Search
        List<BM25Engine.BM25ScoredResult> bm25Results = bm25Engine.search(query, poolSize);
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

            double rawVector = vectorScoreMap.getOrDefault(id, 0.0);
            // Normalize cosine similarity from [-1, 1] to [0, 1]
            double normVector = Math.max(0.0, Math.min(1.0, (rawVector + 1.0) / 2.0));

            double rawBm25 = bm25ScoreMap.getOrDefault(id, 0.0);
            double normBm25 = Math.max(0.0, rawBm25 / maxBm25);

            double hybridScore = (alpha * normVector) + ((1.0 - alpha) * normBm25);

            SearchResultChunkDto dto = new SearchResultChunkDto(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getFilePath(),
                    chunk.getDocumentName(),
                    chunk.getPageNumber(),
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

        List<SearchResultChunkDto> topChunks = scoredCandidates.size() > topK
                ? scoredCandidates.subList(0, topK)
                : scoredCandidates;

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

        return new SearchResponseDto(
                query,
                synthesizedResponse,
                topChunks,
                new ArrayList<>(distinctSources),
                new ArrayList<>(distinctPages),
                searchLatency,
                synthesisLatency,
                llmService.getModelName()
        );
    }
}

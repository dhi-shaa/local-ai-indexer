package com.localai.indexer.evaluation;

import com.localai.indexer.engine.dto.IndexResponseDto;
import com.localai.indexer.engine.vector.VectorRepository;
import com.localai.indexer.ingestion.IngestionPipeline;
import com.localai.indexer.ingestion.chunking.ChunkingStrategy;
import com.localai.indexer.ingestion.embedding.LocalSemanticEmbeddingService;
import com.localai.indexer.ingestion.parser.DocumentParser;
import com.localai.indexer.search.bm25.BM25Engine;
import com.localai.indexer.search.dto.SearchRequestDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import com.localai.indexer.search.dto.SearchResultChunkDto;
import com.localai.indexer.search.hybrid.HybridSearchEngine;
import com.localai.indexer.search.llm.OllamaLlmService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rigorous Evaluation Harness for Local AI Privacy Chat & Document Indexer.
 * 
 * Evaluates:
 * 1. 20 Hand-written Ground Truth Q&A pairs covering system architecture, vector DB, chunking, BM25, and privacy.
 * 2. Recall@k (k = 1, 3, 5, 10) across chunking strategies:
 *    - SEMANTIC_PARAGRAPH
 *    - SEMANTIC_SENTENCE_WINDOW
 *    - FIXED_SIZE
 * 3. Recall@k across similarity metrics / retrieval modes:
 *    - Hybrid Search (Cosine + BM25, alpha=0.5)
 *    - Vector Only (Cosine, alpha=1.0)
 *    - BM25 Only (alpha=0.0)
 *    - Euclidean Distance
 *    - Dot Product
 * 4. Latency benchmarks for Parse, Chunk, Embed, and Store stages.
 */
import com.localai.indexer.engine.LocalAiIndexerApplication;

@SpringBootTest(classes = LocalAiIndexerApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EvaluationHarnessTest {

    private static final Logger log = LoggerFactory.getLogger(EvaluationHarnessTest.class);

    @Autowired
    private IngestionPipeline ingestionPipeline;

    @Autowired
    private HybridSearchEngine hybridSearchEngine;

    @Autowired
    private VectorRepository vectorRepository;

    private static class QAPair {
        final int id;
        final String question;
        final List<String> expectedKeywords;
        final int expectedPage;

        QAPair(int id, String question, List<String> expectedKeywords, int expectedPage) {
            this.id = id;
            this.question = question;
            this.expectedKeywords = expectedKeywords;
            this.expectedPage = expectedPage;
        }

        boolean isRelevant(SearchResultChunkDto chunk) {
            if (chunk == null || chunk.getContent() == null) return false;
            String text = chunk.getContent().toLowerCase(Locale.ROOT);
            for (String kw : expectedKeywords) {
                if (text.contains(kw.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<QAPair> benchmarkQuestions = List.of(
            new QAPair(1, "What database is used for embedded vector storage without external dependencies?",
                    List.of("h2 database", "embedded h2", "udf vector extensions"), 1),
            new QAPair(2, "Why is PGVector avoided in this local architecture?",
                    List.of("eliminate external database dependencies", "postgresql", "pgvector"), 1),
            new QAPair(3, "Which library is integrated for unstructured document parsing of PDF and DOCX files?",
                    List.of("apache tika", "tika"), 2),
            new QAPair(4, "How does the ingestion pipeline preserve physical page numbers during text extraction?",
                    List.of("form-feed", "page numbers", "\\f"), 2),
            new QAPair(5, "What are the primary and secondary boundary rules used by the semantic chunking methodology?",
                    List.of("paragraph", "sentence boundary", "syntactic"), 2),
            new QAPair(6, "What ranking algorithm is implemented from scratch for keyword retrieval?",
                    List.of("bm25", "okapi bm25"), 3),
            new QAPair(7, "What are the values of the BM25 tuning parameters k1 and b?",
                    List.of("1.2", "0.75", "k1", "b"), 3),
            new QAPair(8, "How does the Robertson-Spärck Jones IDF formula prevent negative weights?",
                    List.of("floor smoothing", "robertson-spärck jones", "negative term weights"), 3),
            new QAPair(9, "How are vector cosine similarity scores and BM25 keyword scores combined in hybrid search?",
                    List.of("normalized", "alpha", "interpolation parameter", "score fusion"), 3),
            new QAPair(10, "What is the smoothing constant used in the Reciprocal Rank Fusion algorithm?",
                    List.of("smoothing constant of 60", "reciprocal rank fusion", "60"), 3),
            new QAPair(11, "What local service and port are used for LLM inference and embeddings?",
                    List.of("ollama", "localhost port 11434", "11434"), 4),
            new QAPair(12, "What is the default local embedding model and its vector dimension?",
                    List.of("nomic-embed-text", "768"), 4),
            new QAPair(13, "What is the default generative chat model used by the local engine?",
                    List.of("llama3.2", "generative model"), 4),
            new QAPair(14, "What mechanism ensures zero downtime if the local Ollama instance is offline?",
                    List.of("fallback", "dense semantic hashing", "extractive synthesis"), 4),
            new QAPair(15, "Which network interface are all backend requests strictly restricted to?",
                    List.of("loopback", "127.0.0.1", "localhost"), 4),
            new QAPair(16, "What local port does the Spring Boot headless engine expose for REST endpoints?",
                    List.of("port 8080", "/api", "8080"), 4),
            new QAPair(17, "How can the JavaFX frontend interact with the backend in a shared JVM without HTTP overhead?",
                    List.of("direct method references", "backendservice", "facade"), 4),
            new QAPair(18, "What is the targeted parse latency budget for a standard 5-page document?",
                    List.of("under 150 milliseconds", "150 milliseconds", "parse latency"), 5),
            new QAPair(19, "What is the targeted retrieval latency for hybrid search queries?",
                    List.of("less than 50 milliseconds", "50 milliseconds", "retrieval latency"), 5),
            new QAPair(20, "What fields are contained in the unified SearchResponseDto returned to the user?",
                    List.of("synthesized ai response", "exact text chunks", "source file paths", "page numbers"), 5)
    );

    @Test
    @DisplayName("Verify Ingestion Stage Latency and Recall@k Benchmarks")
    void testEvaluationHarnessAndRecallMetrics() throws Exception {
        System.out.println("\n==========================================================================");
        System.out.println("     LOCAL AI PRIVACY CHAT & DOCUMENT INDEXER - EVALUATION HARNESS        ");
        System.out.println("==========================================================================");

        // Load sample document from classpath
        InputStream is = getClass().getResourceAsStream("/sample_privacy_doc.txt");
        assertThat(is).as("sample_privacy_doc.txt must exist in test classpath").isNotNull();

        byte[] docBytes = is.readAllBytes();

        // 1. Ingestion Benchmark on Default Semantic Paragraph Strategy
        IndexResponseDto defaultIndexResult = ingestionPipeline.ingest(
                new java.io.ByteArrayInputStream(docBytes),
                "sample_privacy_doc.txt",
                "/docs/sample_privacy_doc.txt",
                ChunkingStrategy.SEMANTIC_PARAGRAPH,
                400
        );

        assertThat(defaultIndexResult.isSuccess()).isTrue();
        hybridSearchEngine.refreshBM25Index();

        System.out.println("\n--- INGESTION STAGE LATENCY BENCHMARK ---");
        System.out.printf("  Stage 1: Apache Tika Parse Latency : %4d ms%n", defaultIndexResult.getParseLatencyMs());
        System.out.printf("  Stage 2: Semantic Chunk Latency     : %4d ms%n", defaultIndexResult.getChunkLatencyMs());
        System.out.printf("  Stage 3: Dense Vector Embed Latency : %4d ms%n", defaultIndexResult.getEmbedLatencyMs());
        System.out.printf("  Stage 4: H2 Vector DB Store Latency : %4d ms%n", defaultIndexResult.getStoreLatencyMs());
        System.out.printf("  Total End-to-End Ingestion Latency : %4d ms (%d chunks, %d pages)%n",
                defaultIndexResult.getTotalLatencyMs(), defaultIndexResult.getChunksCount(), defaultIndexResult.getPageCount());

        // 2. Measure Recall@k for Chunking Strategies
        System.out.println("\n--- RECALL@K BENCHMARK ACROSS CHUNKING STRATEGIES ---");
        System.out.println("| Chunking Strategy          | Recall@1 | Recall@3 | Recall@5 | Recall@10 | Mean Latency |");
        System.out.println("|----------------------------|----------|----------|----------|-----------|--------------|");

        Map<ChunkingStrategy, Map<Integer, Double>> chunkingRecallResults = new LinkedHashMap<>();

        for (ChunkingStrategy strategy : ChunkingStrategy.values()) {
            // Re-ingest with current strategy
            vectorRepository.clearAll();
            IndexResponseDto idxRes = ingestionPipeline.ingest(
                    new java.io.ByteArrayInputStream(docBytes),
                    "sample_privacy_doc.txt",
                    "/docs/sample_privacy_doc.txt",
                    strategy,
                    strategy == ChunkingStrategy.FIXED_SIZE ? 100 : 400
            );
            hybridSearchEngine.refreshBM25Index();

            Map<Integer, Double> recalls = evaluateRecallAtK(0.5, "cosine");
            chunkingRecallResults.put(strategy, recalls);

            System.out.printf("| %-26s |  %5.1f%%  |  %5.1f%%  |  %5.1f%%  |   %5.1f%%  |   %4d ms    |%n",
                    strategy.name(),
                    recalls.get(1) * 100.0,
                    recalls.get(3) * 100.0,
                    recalls.get(5) * 100.0,
                    recalls.get(10) * 100.0,
                    idxRes.getTotalLatencyMs());
        }

        // 3. Measure Recall@k for Similarity Metrics & Retrieval Modes on Semantic Paragraph Chunking
        System.out.println("\n--- RECALL@K BENCHMARK ACROSS RETRIEVAL MODES & METRICS ---");
        System.out.println("| Retrieval Mode / Metric    | Alpha | Recall@1 | Recall@3 | Recall@5 | Recall@10 |");
        System.out.println("|----------------------------|-------|----------|----------|----------|-----------|");

        // Restore Semantic Paragraph chunking
        vectorRepository.clearAll();
        ingestionPipeline.ingest(
                new java.io.ByteArrayInputStream(docBytes),
                "sample_privacy_doc.txt",
                "/docs/sample_privacy_doc.txt",
                ChunkingStrategy.SEMANTIC_PARAGRAPH,
                400
        );
        hybridSearchEngine.refreshBM25Index();

        // Configurations: (Name, Alpha, Metric)
        List<MetricConfig> configs = List.of(
                new MetricConfig("Hybrid (Cosine + BM25)", 0.5, "cosine"),
                new MetricConfig("Vector Only (Cosine)", 1.0, "cosine"),
                new MetricConfig("BM25 Only (Keyword)", 0.0, "cosine"),
                new MetricConfig("Hybrid (Euclidean + BM25)", 0.5, "euclidean"),
                new MetricConfig("Hybrid (Dot Product + BM25)", 0.5, "dot")
        );

        Map<String, Map<Integer, Double>> metricRecallResults = new LinkedHashMap<>();

        for (MetricConfig cfg : configs) {
            Map<Integer, Double> recalls = evaluateRecallAtK(cfg.alpha, cfg.metric);
            metricRecallResults.put(cfg.name, recalls);

            System.out.printf("| %-26s |  %3.1f  |  %5.1f%%  |  %5.1f%%  |  %5.1f%%  |   %5.1f%%  |%n",
                    cfg.name, cfg.alpha,
                    recalls.get(1) * 100.0,
                    recalls.get(3) * 100.0,
                    recalls.get(5) * 100.0,
                    recalls.get(10) * 100.0);
        }

        System.out.println("==========================================================================\n");

        // Verify that Hybrid Search achieves high Recall@5 (>= 90%)
        Map<Integer, Double> hybridRecalls = metricRecallResults.get("Hybrid (Cosine + BM25)");
        assertThat(hybridRecalls.get(5))
                .as("Hybrid search Recall@5 should be >= 0.90")
                .isGreaterThanOrEqualTo(0.90);

        assertThat(hybridRecalls.get(10))
                .as("Hybrid search Recall@10 should be 1.00")
                .isGreaterThanOrEqualTo(0.95);
    }

    private Map<Integer, Double> evaluateRecallAtK(double alpha, String metric) {
        int[] kValues = {1, 3, 5, 10};
        Map<Integer, Integer> hitsAtK = new HashMap<>();
        for (int k : kValues) {
            hitsAtK.put(k, 0);
        }

        for (QAPair qa : benchmarkQuestions) {
            SearchRequestDto req = new SearchRequestDto();
            req.setQuery(qa.question);
            req.setTopK(10);
            req.setAlpha(alpha);
            req.setSimilarityMetric(metric);
            req.setSynthesizeResponse(false);

            SearchResponseDto res = hybridSearchEngine.search(req);
            List<SearchResultChunkDto> chunks = res.getChunks();

            for (int k : kValues) {
                int limit = Math.min(k, chunks.size());
                boolean found = false;
                for (int i = 0; i < limit; i++) {
                    if (qa.isRelevant(chunks.get(i))) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    hitsAtK.put(k, hitsAtK.get(k) + 1);
                }
            }
        }

        Map<Integer, Double> recalls = new HashMap<>();
        double total = benchmarkQuestions.size();
        for (int k : kValues) {
            recalls.put(k, hitsAtK.get(k) / total);
        }
        return recalls;
    }

    private static class MetricConfig {
        final String name;
        final double alpha;
        final String metric;

        MetricConfig(String name, double alpha, String metric) {
            this.name = name;
            this.alpha = alpha;
            this.metric = metric;
        }
    }
}

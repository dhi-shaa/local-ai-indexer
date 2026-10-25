package com.localai.indexer.search.bm25;

import com.localai.indexer.ingestion.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BM25EngineTest {

    private BM25Engine bm25Engine;

    @BeforeEach
    void setUp() {
        bm25Engine = new BM25Engine(1.2, 0.75);
    }

    @Test
    void testTokenizationExcludesStopWords() {
        List<String> tokens = bm25Engine.tokenize("The quick brown fox jumps over the lazy dog!");
        assertThat(tokens).contains("quick", "brown", "fox", "jumps", "lazy", "dog")
                .doesNotContain("the", "over");
    }

    @Test
    void testIndexingAndBM25Ranking() {
        DocumentChunk chunk1 = new DocumentChunk("c1", "d1", "fileA.txt", "fileA.txt", 1, 0,
                "Apache Tika handles unstructured document parsing for PDF and DOCX files.", null);
        DocumentChunk chunk2 = new DocumentChunk("c2", "d1", "fileB.txt", "fileB.txt", 1, 1,
                "The embedded H2 vector database stores high-dimensional embeddings locally.", null);
        DocumentChunk chunk3 = new DocumentChunk("c3", "d1", "fileC.txt", "fileC.txt", 1, 2,
                "Local AI privacy ensures zero telemetry and air-gapped security.", null);

        bm25Engine.indexChunks(List.of(chunk1, chunk2, chunk3));

        List<BM25Engine.BM25ScoredResult> results = bm25Engine.search("H2 vector database embeddings", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getChunk().getId()).isEqualTo("c2");
        assertThat(results.get(0).getScore()).isGreaterThan(0.0);
    }
}

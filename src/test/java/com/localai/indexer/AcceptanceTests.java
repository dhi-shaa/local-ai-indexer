package com.localai.indexer;

import com.localai.indexer.engine.dto.IndexResponseDto;
import com.localai.indexer.engine.service.BackendService;
import com.localai.indexer.ingestion.chunking.ChunkingStrategy;
import com.localai.indexer.ingestion.embedding.EmbeddingService;
import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.search.dto.SearchRequestDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import com.localai.indexer.search.dto.SearchResultChunkDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.localai.indexer.engine.LocalAiIndexerApplication.class)
public class AcceptanceTests {

    @Autowired
    private BackendService backendService;

    @Autowired
    private EmbeddingService embeddingService;

    private final File kerberosPdf = new File("C:\\Users\\delld\\Downloads\\kerberos_authentication.pdf");
    private final File mlPrepPdf = new File("C:\\Users\\delld\\Downloads\\ML Lab Sem Prep.pdf");

    @Test
    @DisplayName("Acceptance Test 1 & 2: Kerberos Authentication PDF indexing, retrieval, and threat/mitigation pairing")
    void testAcceptanceKerberos() {
        if (!kerberosPdf.exists()) {
            System.out.println("SKIPPING: kerberos_authentication.pdf not found at " + kerberosPdf.getAbsolutePath());
            return;
        }

        // Clear previous data
        backendService.clearAll();

        // Upload and index kerberos_authentication.pdf
        IndexResponseDto indexResult = backendService.indexDocument(new com.localai.indexer.engine.dto.IndexRequestDto(
                kerberosPdf.getAbsolutePath(), "SEMANTIC_PARAGRAPH", 400
        ));
        assertThat(indexResult.isSuccess()).isTrue();
        assertThat(indexResult.getPageCount()).isEqualTo(11);

        System.out.println("\n========================================================");
        System.out.println("ACCEPTANCE TEST 1: 'what are the threats and limitations of Kerberos?'");
        System.out.println("========================================================");
        System.out.println("Active Embedding Service: " + embeddingService.getModelName());

        SearchRequestDto req1 = new SearchRequestDto("what are the threats and limitations of Kerberos?", 5, 0.5);
        req1.setSynthesizeResponse(true);
        SearchResponseDto resp1 = backendService.search(req1);

        System.out.println("Retrieved Chunks (" + resp1.getChunks().size() + "):");
        for (int i = 0; i < resp1.getChunks().size(); i++) {
            SearchResultChunkDto chunk = resp1.getChunks().get(i);
            System.out.println(String.format("  [%d] Page %s (ID: %s, BM25: %.4f, Vector: %.4f, Hybrid: %.4f)",
                    i + 1, chunk.getPageLabel(), chunk.getChunkId(),
                    chunk.getBm25Score(), chunk.getVectorScore(), chunk.getHybridScore()));
            System.out.println("      Snippet: " + chunk.getContent().replaceAll("\\s+", " ").substring(0, Math.min(160, chunk.getContent().length())) + "...");
        }

        System.out.println("\nSynthesized AI Response:\n" + resp1.getAiResponse());

        // Assert slide 9 chunk is in rank 1-3
        boolean slide9InTop3 = false;
        for (int i = 0; i < Math.min(3, resp1.getChunks().size()); i++) {
            SearchResultChunkDto chunk = resp1.getChunks().get(i);
            if (chunk.getPageNumber() == 9 || "Page 9".equals(chunk.getPageLabel())) {
                slide9InTop3 = true;
                break;
            }
        }
        assertThat(slide9InTop3).as("Slide 9 chunk must be ranked in top 1-3").isTrue();

        System.out.println("\n========================================================");
        System.out.println("ACCEPTANCE TEST 2: 'krbtgt' and 'Kerberoasting'");
        System.out.println("========================================================");
        SearchRequestDto req2 = new SearchRequestDto("krbtgt Kerberoasting", 5, 0.5);
        req2.setSynthesizeResponse(false);
        SearchResponseDto resp2 = backendService.search(req2);

        System.out.println("Retrieved Chunks for krbtgt/Kerberoasting (" + resp2.getChunks().size() + "):");
        boolean foundSlide9 = false;
        for (int i = 0; i < resp2.getChunks().size(); i++) {
            SearchResultChunkDto chunk = resp2.getChunks().get(i);
            System.out.println(String.format("  [%d] %s (Page: %s, Hybrid: %.4f)",
                    i + 1, chunk.getDocumentName(), chunk.getPageLabel(), chunk.getHybridScore()));
            if (chunk.getPageNumber() == 9 || "Page 9".equals(chunk.getPageLabel())) {
                foundSlide9 = true;
            }
        }
        assertThat(foundSlide9).as("Slide 9 chunk must be returned for krbtgt/Kerberoasting").isTrue();
    }

    @Test
    @DisplayName("Acceptance Test 3: ML Lab Sem Prep.pdf - zero mock/seeded citations")
    void testAcceptanceMlPrep() {
        if (!mlPrepPdf.exists()) {
            System.out.println("SKIPPING: ML Lab Sem Prep.pdf not found at " + mlPrepPdf.getAbsolutePath());
            return;
        }

        // Upload and index ML Lab Sem Prep.pdf
        IndexResponseDto indexResult = backendService.indexDocument(new com.localai.indexer.engine.dto.IndexRequestDto(
                mlPrepPdf.getAbsolutePath(), "SEMANTIC_PARAGRAPH", 400
        ));
        assertThat(indexResult.isSuccess()).isTrue();

        System.out.println("\n========================================================");
        System.out.println("ACCEPTANCE TEST 3: 'what is ID3 and why entropy?'");
        System.out.println("========================================================");
        System.out.println("Active Embedding Service: " + embeddingService.getModelName());

        SearchRequestDto req = new SearchRequestDto("what is ID3 and why entropy?", 5, 0.5);
        req.setSynthesizeResponse(true);
        SearchResponseDto resp = backendService.search(req);

        System.out.println("Retrieved Chunks (" + resp.getChunks().size() + "):");
        for (int i = 0; i < resp.getChunks().size(); i++) {
            SearchResultChunkDto chunk = resp.getChunks().get(i);
            System.out.println(String.format("  [%d] Document: %s, Page: %s, Hybrid: %.4f",
                    i + 1, chunk.getDocumentName(), chunk.getPageLabel(), chunk.getHybridScore()));

            // Verify no mock/seeded citations
            assertThat(chunk.getChunkId()).doesNotStartWith("chk_hyb");
            assertThat(chunk.getChunkId()).doesNotStartWith("chk_priv");
            assertThat(chunk.getChunkId()).doesNotStartWith("chk_vec");
            assertThat(chunk.getDocumentName()).doesNotContain("ADR-001");
            assertThat(chunk.getDocumentName()).doesNotContain("RecursiveTokenChunker");
            assertThat(chunk.getDocumentName()).doesNotContain("pipeline-spec");
        }

        System.out.println("\nSynthesized AI Response:\n" + resp.getAiResponse());
    }

    @Test
    @DisplayName("Acceptance Test 4: Query not in any document -> zero or few citations and explicit notice")
    void testAcceptanceUnrelatedQuery() {
        System.out.println("\n========================================================");
        System.out.println("ACCEPTANCE TEST 4: Query something not in any document");
        System.out.println("========================================================");

        SearchRequestDto req = new SearchRequestDto("quantum teleportation interstellar dark matter warp drive", 5, 0.5);
        req.setMinScore(0.35); // Enforce threshold
        req.setSynthesizeResponse(true);
        SearchResponseDto resp = backendService.search(req);

        System.out.println("Retrieved Chunks Count: " + resp.getChunks().size());
        System.out.println("AI Response: " + resp.getAiResponse());

        assertThat(resp.getChunks().size()).isLessThanOrEqualTo(1);
        if (resp.getChunks().isEmpty()) {
            assertThat(resp.getAiResponse()).contains("do not contain");
        }
    }

    @Test
    @DisplayName("Acceptance Test 5: Chunk boundary and length integrity")
    void testAcceptanceChunkBoundaries() {
        if (!kerberosPdf.exists()) return;

        List<DocumentChunk> chunks = backendService.getDocumentChunks(
                backendService.listDocuments().get(0).get("DOCUMENT_ID").toString()
        );

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent().trim();
            assertThat(content).isNotEmpty();

            // Verify no mid-word starts like "ine password" or "es the server"
            assertThat(content).doesNotStartWith("ine password");
            assertThat(content).doesNotStartWith("es the server");
            assertThat(content).doesNotStartWith("O ine");

            // Minimum length check (unless page itself is short)
            if (!content.contains("/ 11") && content.length() > 50) {
                assertThat(content.length()).isGreaterThanOrEqualTo(80);
            }
        }
    }
}

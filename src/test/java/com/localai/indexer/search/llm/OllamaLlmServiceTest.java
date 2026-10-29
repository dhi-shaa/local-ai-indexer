package com.localai.indexer.search.llm;

import com.localai.indexer.model.HybridSearchResultDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import com.localai.indexer.search.dto.SearchResultChunkDto;
import com.localai.indexer.service.MockPipelineBackendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OllamaLlmServiceTest {

    @Test
    @DisplayName("Verify Ollama offline fallback handling returns helpful instruction")
    public void testOllamaOfflineFallback() {
        OllamaLlmService service = new OllamaLlmService("http://127.0.0.1:11434", "phi3", 2);

        List<SearchResultChunkDto> chunks = List.of(
            new SearchResultChunkDto("chk_01", "doc1", "doc1.txt", "doc1.txt", 1, 0, "Airgap policy text", 0.9, 1.2, 0.95),
            new SearchResultChunkDto("chk_02", "doc2", "doc2.txt", "doc2.txt", 2, 1, "H2 vector store text", 0.85, 1.0, 0.88)
        );

        String answer = service.synthesizeAnswer("What is the air-gap architecture?", chunks);
        assertNotNull(answer);
        assertFalse(answer.isBlank());

        // Either live Ollama output or the graceful offline fallback
        if (!service.isAvailable()) {
            assertEquals("Ollama is not running locally. Please ensure Ollama is active on port 11434.", answer);
        }
    }

    @Test
    @DisplayName("Verify SearchResponseDto aiResponse accessor and serialization")
    public void testSearchResponseDtoAiResponse() {
        SearchResponseDto dto = new SearchResponseDto();
        dto.setAiResponse("Test synthesized AI response");
        assertEquals("Test synthesized AI response", dto.getAiResponse());
        assertEquals("Test synthesized AI response", dto.getSynthesizedResponse());
    }

    @Test
    @DisplayName("Verify MockPipelineBackendService Ollama integration and citations")
    public void testMockPipelineBackendServiceSearch() {
        MockPipelineBackendService service = MockPipelineBackendService.getInstance();
        service.registerIngestedDocument("sample_arch.md", 1, List.of(
            new com.localai.indexer.model.DocumentChunkDto(
                "chk_user_arch_01", "sample_arch.md", 1, "Architecture",
                "Local vector indexing and retrieval uses H2 database and BM25 search.",
                new double[32], 0.0, 0.0
            )
        ), null);

        HybridSearchResultDto result = service.executeSearch("Explain local vector indexing and retrieval");

        assertNotNull(result);
        assertNotNull(result.answer());
        assertEquals(result.answer(), result.aiResponse());
        assertFalse(result.citations().isEmpty());
        assertTrue(result.citations().size() <= 5, "Expected at most 5 capped citations, got: " + result.citations().size());

        // When Ollama is offline, fallback message must be returned cleanly
        if (result.answer().startsWith("Ollama is not running locally")) {
            assertEquals("Ollama is not running locally. Please ensure Ollama is active on port 11434.", result.answer());
        }
    }

    @Test
    @DisplayName("Verify user-uploaded documents are boosted and prioritized over internal system docs")
    public void testUserUploadedDocumentPriorityInMockService() {
        MockPipelineBackendService service = MockPipelineBackendService.getInstance();
        String fileName = "kerberos_authentication.pdf";
        String chunkContent = "Kerberos is a network authentication protocol that works on the basis of tickets to allow nodes to prove their identity securely.";

        com.localai.indexer.model.DocumentChunkDto userChunk = new com.localai.indexer.model.DocumentChunkDto(
            "custom_kerberos_01",
            fileName,
            1,
            "User PDF (" + fileName + ")",
            chunkContent,
            new double[32],
            0.0,
            0.0
        );

        service.registerIngestedDocument(
            fileName,
            1,
            List.of(userChunk),
            new com.localai.indexer.model.PipelineStageTiming(20, 10, 15, 5)
        );

        HybridSearchResultDto result = service.executeSearch("How does Kerberos authentication work?");
        assertNotNull(result);
        assertFalse(result.citations().isEmpty());

        // First citation should be the user-uploaded document
        assertEquals(fileName, result.citations().get(0).fileName());
        assertTrue(result.citations().get(0).textSnippet().contains("Kerberos"));
    }

    @Test
    @DisplayName("Verify SearchResultChunkDto getText accessor")
    public void testSearchResultChunkDtoGetText() {
        SearchResultChunkDto chunk = new SearchResultChunkDto();
        chunk.setContent("Sample Plain Text Chunk Content");
        assertEquals("Sample Plain Text Chunk Content", chunk.getText());
    }
}

package com.localai.indexer.search.llm;

import com.localai.indexer.search.dto.SearchResultChunkDto;
import java.util.List;

/**
 * Interface for LLM response synthesis based on retrieved document chunks.
 */
public interface LlmService {

    /**
     * Synthesize an AI response answering the query based strictly on the retrieved context chunks.
     */
    String synthesizeAnswer(String query, List<SearchResultChunkDto> contextChunks);

    /**
     * Check if local LLM service (Ollama) is available.
     */
    boolean isAvailable();

    /**
     * Active LLM model name.
     */
    String getModelName();
}

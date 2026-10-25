package com.localai.indexer.model;

import java.util.List;

/**
 * Data Transfer Object representing hybrid search response with ranked citations.
 */
public record HybridSearchResultDto(
    String query,
    String answer,
    long latencyMs,
    List<SourceCitation> citations
) {
    public String aiResponse() {
        return answer;
    }
}

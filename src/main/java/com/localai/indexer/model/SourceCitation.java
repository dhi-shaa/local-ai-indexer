package com.localai.indexer.model;

/**
 * Represents a source citation returned by hybrid search.
 */
public record SourceCitation(
    String chunkId,
    String fileName,
    int pageNumber,
    double score,
    String sectionHeader,
    String textSnippet
) {
    public String getFormattedScore() {
        return String.format("%.2f", score);
    }
}

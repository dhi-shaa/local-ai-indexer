package com.localai.indexer.model;

/**
 * Represents a source citation returned by hybrid search.
 */
public record SourceCitation(
    String chunkId,
    String fileName,
    int pageNumber,
    int startPageNumber,
    int endPageNumber,
    int chunkIndex,
    double score,
    String sectionHeader,
    String textSnippet
) {
    public SourceCitation(String chunkId, String fileName, int pageNumber, double score, String sectionHeader, String textSnippet) {
        this(chunkId, fileName, pageNumber, pageNumber, pageNumber, 0, score, sectionHeader, textSnippet);
    }

    public String getFormattedScore() {
        return String.format("%.2f", score);
    }

    public String getPageDisplay() {
        int start = startPageNumber > 0 ? startPageNumber : pageNumber;
        int end = endPageNumber > 0 ? endPageNumber : start;
        if (start == end || end <= 0) {
            return "Page " + start;
        } else {
            return "pp. " + start + "-" + end;
        }
    }

    public int getDisplayChunkIndex() {
        if (chunkIndex > 0) return chunkIndex;
        if (chunkId != null && chunkId.contains("_c")) {
            try {
                String sub = chunkId.substring(chunkId.lastIndexOf("_c") + 2);
                return Integer.parseInt(sub) + 1; // 1-based display
            } catch (Exception ignored) {}
        }
        return 1;
    }
}

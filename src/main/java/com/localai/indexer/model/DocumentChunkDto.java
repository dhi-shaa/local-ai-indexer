package com.localai.indexer.model;

import java.util.Arrays;

/**
 * Representation of an indexed document chunk with embedding vector and projected 2D coordinates.
 */
public record DocumentChunkDto(
    String chunkId,
    String fileName,
    int pageNumber,
    String category,
    String textSnippet,
    double[] embedding,
    double x2d,
    double y2d
) {
    public DocumentChunkDto withCoordinates(double x, double y) {
        return new DocumentChunkDto(chunkId, fileName, pageNumber, category, textSnippet, embedding, x, y);
    }

    @Override
    public String toString() {
        return "[" + chunkId + "] " + fileName + " (p." + pageNumber + ", " + category + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentChunkDto that)) return false;
        return chunkId != null && chunkId.equals(that.chunkId);
    }

    @Override
    public int hashCode() {
        return chunkId != null ? chunkId.hashCode() : 0;
    }
}

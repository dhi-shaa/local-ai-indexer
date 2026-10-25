package com.localai.indexer.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified response DTO containing the synthesized AI response, exact text chunks,
 * source file paths, and page numbers.
 */
public class SearchResponseDto {
    private String query;
    private String synthesizedResponse;
    private List<SearchResultChunkDto> chunks;
    private List<String> sourceFilePaths;
    private List<Integer> pageNumbers;
    private long searchLatencyMs;
    private long synthesisLatencyMs;
    private long totalLatencyMs;
    private String modelUsed;

    public SearchResponseDto() {
        this.chunks = new ArrayList<>();
        this.sourceFilePaths = new ArrayList<>();
        this.pageNumbers = new ArrayList<>();
    }

    public SearchResponseDto(String query, String synthesizedResponse, List<SearchResultChunkDto> chunks,
                             List<String> sourceFilePaths, List<Integer> pageNumbers,
                             long searchLatencyMs, long synthesisLatencyMs, String modelUsed) {
        this.query = query;
        this.synthesizedResponse = synthesizedResponse;
        this.chunks = chunks != null ? chunks : new ArrayList<>();
        this.sourceFilePaths = sourceFilePaths != null ? sourceFilePaths : new ArrayList<>();
        this.pageNumbers = pageNumbers != null ? pageNumbers : new ArrayList<>();
        this.searchLatencyMs = searchLatencyMs;
        this.synthesisLatencyMs = synthesisLatencyMs;
        this.totalLatencyMs = searchLatencyMs + synthesisLatencyMs;
        this.modelUsed = modelUsed;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSynthesizedResponse() {
        return synthesizedResponse;
    }

    public void setSynthesizedResponse(String synthesizedResponse) {
        this.synthesizedResponse = synthesizedResponse;
    }

    public List<SearchResultChunkDto> getChunks() {
        return chunks;
    }

    public void setChunks(List<SearchResultChunkDto> chunks) {
        this.chunks = chunks;
    }

    public List<String> getSourceFilePaths() {
        return sourceFilePaths;
    }

    public void setSourceFilePaths(List<String> sourceFilePaths) {
        this.sourceFilePaths = sourceFilePaths;
    }

    public List<Integer> getPageNumbers() {
        return pageNumbers;
    }

    public void setPageNumbers(List<Integer> pageNumbers) {
        this.pageNumbers = pageNumbers;
    }

    public long getSearchLatencyMs() {
        return searchLatencyMs;
    }

    public void setSearchLatencyMs(long searchLatencyMs) {
        this.searchLatencyMs = searchLatencyMs;
    }

    public long getSynthesisLatencyMs() {
        return synthesisLatencyMs;
    }

    public void setSynthesisLatencyMs(long synthesisLatencyMs) {
        this.synthesisLatencyMs = synthesisLatencyMs;
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs;
    }

    public void setTotalLatencyMs(long totalLatencyMs) {
        this.totalLatencyMs = totalLatencyMs;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }
}

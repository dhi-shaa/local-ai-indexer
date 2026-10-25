package com.localai.indexer.engine.dto;

import java.util.List;

public class IndexResponseDto {
    private boolean success;
    private String documentId;
    private String documentName;
    private String filePath;
    private int chunksCount;
    private int pageCount;
    private long parseLatencyMs;
    private long chunkLatencyMs;
    private long embedLatencyMs;
    private long storeLatencyMs;
    private long totalLatencyMs;
    private String errorMessage;

    public IndexResponseDto() {}

    public static IndexResponseDto failure(String filePath, String errorMessage) {
        IndexResponseDto dto = new IndexResponseDto();
        dto.setSuccess(false);
        dto.setFilePath(filePath);
        dto.setErrorMessage(errorMessage);
        return dto;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getChunksCount() {
        return chunksCount;
    }

    public void setChunksCount(int chunksCount) {
        this.chunksCount = chunksCount;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public long getParseLatencyMs() {
        return parseLatencyMs;
    }

    public void setParseLatencyMs(long parseLatencyMs) {
        this.parseLatencyMs = parseLatencyMs;
    }

    public long getChunkLatencyMs() {
        return chunkLatencyMs;
    }

    public void setChunkLatencyMs(long chunkLatencyMs) {
        this.chunkLatencyMs = chunkLatencyMs;
    }

    public long getEmbedLatencyMs() {
        return embedLatencyMs;
    }

    public void setEmbedLatencyMs(long embedLatencyMs) {
        this.embedLatencyMs = embedLatencyMs;
    }

    public long getStoreLatencyMs() {
        return storeLatencyMs;
    }

    public void setStoreLatencyMs(long storeLatencyMs) {
        this.storeLatencyMs = storeLatencyMs;
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs;
    }

    public void setTotalLatencyMs(long totalLatencyMs) {
        this.totalLatencyMs = totalLatencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

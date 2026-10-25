package com.localai.indexer.engine.dto;

import java.util.Map;

public class BackendStatusDto {
    private String status;
    private long indexedDocumentsCount;
    private long totalChunksCount;
    private boolean ollamaAvailable;
    private String ollamaBaseUrl;
    private String embeddingModel;
    private String chatModel;
    private String vectorStorageEngine;
    private Map<String, Object> memoryUsage;

    public BackendStatusDto() {}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getIndexedDocumentsCount() {
        return indexedDocumentsCount;
    }

    public void setIndexedDocumentsCount(long indexedDocumentsCount) {
        this.indexedDocumentsCount = indexedDocumentsCount;
    }

    public long getTotalChunksCount() {
        return totalChunksCount;
    }

    public void setTotalChunksCount(long totalChunksCount) {
        this.totalChunksCount = totalChunksCount;
    }

    public boolean isOllamaAvailable() {
        return ollamaAvailable;
    }

    public void setOllamaAvailable(boolean ollamaAvailable) {
        this.ollamaAvailable = ollamaAvailable;
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getVectorStorageEngine() {
        return vectorStorageEngine;
    }

    public void setVectorStorageEngine(String vectorStorageEngine) {
        this.vectorStorageEngine = vectorStorageEngine;
    }

    public Map<String, Object> getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Map<String, Object> memoryUsage) {
        this.memoryUsage = memoryUsage;
    }
}

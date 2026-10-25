package com.localai.indexer.engine.dto;

public class IndexRequestDto {
    private String filePath;
    private String strategy = "SEMANTIC_PARAGRAPH";
    private int targetChunkSize = 400;

    public IndexRequestDto() {}

    public IndexRequestDto(String filePath) {
        this.filePath = filePath;
    }

    public IndexRequestDto(String filePath, String strategy) {
        this.filePath = filePath;
        this.strategy = strategy;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getTargetChunkSize() {
        return targetChunkSize;
    }

    public void setTargetChunkSize(int targetChunkSize) {
        this.targetChunkSize = targetChunkSize;
    }
}

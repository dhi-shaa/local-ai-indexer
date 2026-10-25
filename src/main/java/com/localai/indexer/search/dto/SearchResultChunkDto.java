package com.localai.indexer.search.dto;

/**
 * Detailed information for an individual matched chunk returned in hybrid search.
 */
public class SearchResultChunkDto {
    private String chunkId;
    private String documentId;
    private String filePath;
    private String documentName;
    private int pageNumber;
    private int chunkIndex;
    private String content;
    private double vectorScore;
    private double bm25Score;
    private double hybridScore;

    public SearchResultChunkDto() {}

    public SearchResultChunkDto(String chunkId, String documentId, String filePath, String documentName,
                                int pageNumber, int chunkIndex, String content,
                                double vectorScore, double bm25Score, double hybridScore) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.filePath = filePath;
        this.documentName = documentName;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.vectorScore = vectorScore;
        this.bm25Score = bm25Score;
        this.hybridScore = hybridScore;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getText() {
        return content != null ? content : "";
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public double getBm25Score() {
        return bm25Score;
    }

    public void setBm25Score(double bm25Score) {
        this.bm25Score = bm25Score;
    }

    public double getHybridScore() {
        return hybridScore;
    }

    public void setHybridScore(double hybridScore) {
        this.hybridScore = hybridScore;
    }
}

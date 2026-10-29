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
    private int startPageNumber;
    private int endPageNumber;
    private int chunkIndex;
    private String content;
    private double vectorScore;
    private double bm25Score;
    private double hybridScore;

    public SearchResultChunkDto() {}

    public SearchResultChunkDto(String chunkId, String documentId, String filePath, String documentName,
                                int pageNumber, int chunkIndex, String content,
                                double vectorScore, double bm25Score, double hybridScore) {
        this(chunkId, documentId, filePath, documentName, pageNumber, pageNumber, chunkIndex, content, vectorScore, bm25Score, hybridScore);
    }

    public SearchResultChunkDto(String chunkId, String documentId, String filePath, String documentName,
                                int startPageNumber, int endPageNumber, int chunkIndex, String content,
                                double vectorScore, double bm25Score, double hybridScore) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.filePath = filePath;
        this.documentName = documentName;
        this.pageNumber = startPageNumber;
        this.startPageNumber = startPageNumber;
        this.endPageNumber = endPageNumber;
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
        if (this.startPageNumber <= 0) this.startPageNumber = pageNumber;
        if (this.endPageNumber <= 0) this.endPageNumber = pageNumber;
    }

    public int getStartPageNumber() {
        return startPageNumber > 0 ? startPageNumber : pageNumber;
    }

    public void setStartPageNumber(int startPageNumber) {
        this.startPageNumber = startPageNumber;
        if (this.pageNumber <= 0) this.pageNumber = startPageNumber;
    }

    public int getEndPageNumber() {
        return endPageNumber > 0 ? endPageNumber : (startPageNumber > 0 ? startPageNumber : pageNumber);
    }

    public void setEndPageNumber(int endPageNumber) {
        this.endPageNumber = endPageNumber;
    }

    public String getPageLabel() {
        int start = getStartPageNumber();
        int end = getEndPageNumber();
        if (start == end || end <= 0) {
            return "Page " + start;
        } else {
            return "pp. " + start + "-" + end;
        }
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

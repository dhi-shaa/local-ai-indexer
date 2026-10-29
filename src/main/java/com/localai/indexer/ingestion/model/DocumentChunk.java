package com.localai.indexer.ingestion.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Model representing an ingested document chunk with text and embedding vector.
 */
public class DocumentChunk {
    private String id;
    private String documentId;
    private String filePath;
    private String documentName;
    private int pageNumber;
    private int startPageNumber;
    private int endPageNumber;
    private int chunkIndex;
    private String content;
    private float[] embedding;
    private Map<String, Object> metadata;

    public DocumentChunk() {
        this.metadata = new HashMap<>();
        this.pageNumber = 1;
        this.startPageNumber = 1;
        this.endPageNumber = 1;
    }

    public DocumentChunk(String id, String documentId, String filePath, String documentName,
                         int pageNumber, int chunkIndex, String content, float[] embedding) {
        this.id = id;
        this.documentId = documentId;
        this.filePath = filePath;
        this.documentName = documentName;
        this.pageNumber = pageNumber;
        this.startPageNumber = pageNumber;
        this.endPageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.metadata = new HashMap<>();
    }

    public DocumentChunk(String id, String documentId, String filePath, String documentName,
                         int startPageNumber, int endPageNumber, int chunkIndex, String content, float[] embedding) {
        this.id = id;
        this.documentId = documentId;
        this.filePath = filePath;
        this.documentName = documentName;
        this.pageNumber = startPageNumber;
        this.startPageNumber = startPageNumber;
        this.endPageNumber = endPageNumber;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.metadata = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        if (this.startPageNumber <= 0) {
            this.startPageNumber = pageNumber;
        }
        if (this.endPageNumber <= 0) {
            this.endPageNumber = pageNumber;
        }
    }

    public int getStartPageNumber() {
        return startPageNumber > 0 ? startPageNumber : pageNumber;
    }

    public void setStartPageNumber(int startPageNumber) {
        this.startPageNumber = startPageNumber;
        if (this.pageNumber <= 0) {
            this.pageNumber = startPageNumber;
        }
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

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "id='" + id + '\'' +
                ", documentName='" + documentName + '\'' +
                ", pageNumber=" + pageNumber +
                ", chunkIndex=" + chunkIndex +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", embeddingDim=" + (embedding != null ? embedding.length : 0) +
                '}';
    }
}

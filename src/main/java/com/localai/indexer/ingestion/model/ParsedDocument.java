package com.localai.indexer.ingestion.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the output of Apache Tika parsing.
 */
public class ParsedDocument {
    private String documentId;
    private String fileName;
    private String filePath;
    private String contentType;
    private Map<String, String> metadata;
    private List<ParsedPage> pages;
    private String fullText;

    public ParsedDocument() {
        this.metadata = new HashMap<>();
        this.pages = new ArrayList<>();
    }

    public ParsedDocument(String documentId, String fileName, String filePath) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.metadata = new HashMap<>();
        this.pages = new ArrayList<>();
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<ParsedPage> getPages() {
        return pages;
    }

    public void setPages(List<ParsedPage> pages) {
        this.pages = pages;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public static class ParsedPage {
        private final int pageNumber;
        private final String text;

        public ParsedPage(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getText() {
            return text;
        }
    }
}

package com.localai.indexer.search.dto;

public class SearchRequestDto {
    private String query;
    private int topK = 5;
    private double alpha = 0.5; // 0.0 = BM25 only, 1.0 = Vector only, 0.5 = Balanced Hybrid
    private String similarityMetric = "cosine"; // cosine, dot, euclidean
    private boolean synthesizeResponse = true;

    public SearchRequestDto() {}

    public SearchRequestDto(String query, int topK, double alpha) {
        this.query = query;
        this.topK = topK;
        this.alpha = alpha;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public String getSimilarityMetric() {
        return similarityMetric;
    }

    public void setSimilarityMetric(String similarityMetric) {
        this.similarityMetric = similarityMetric;
    }

    public boolean isSynthesizeResponse() {
        return synthesizeResponse;
    }

    public void setSynthesizeResponse(boolean synthesizeResponse) {
        this.synthesizeResponse = synthesizeResponse;
    }
}

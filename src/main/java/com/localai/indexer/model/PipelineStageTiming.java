package com.localai.indexer.model;

/**
 * Breakdown of latency in milliseconds across the ingestion pipeline stages.
 */
public record PipelineStageTiming(
    long parseLatencyMs,
    long chunkLatencyMs,
    long embedLatencyMs,
    long storeLatencyMs
) {
    public long totalLatencyMs() {
        return parseLatencyMs + chunkLatencyMs + embedLatencyMs + storeLatencyMs;
    }
}

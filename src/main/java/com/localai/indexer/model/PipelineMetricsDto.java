package com.localai.indexer.model;

/**
 * Live throughput, latency, and pipeline execution metrics consumed by UI.
 */
public record PipelineMetricsDto(
    double throughputDocsPerSec,
    double embeddingLatencyMs,
    PipelineStageTiming stageTiming,
    int activeDocsCount,
    int totalChunksIndexed,
    double memoryUsageMb,
    String currentDocumentName
) {}

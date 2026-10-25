package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;

import java.util.List;

public interface Chunker {
    ChunkingStrategy getStrategy();
    List<DocumentChunk> chunk(ParsedDocument document, int targetSize);
}

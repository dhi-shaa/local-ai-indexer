package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy-driven Chunking Pipeline.
 * Dispatches parsing results to appropriate semantic or fixed chunking implementations.
 */
@Service
public class ChunkingPipeline {

    private final Map<ChunkingStrategy, Chunker> chunkerMap = new EnumMap<>(ChunkingStrategy.class);

    public ChunkingPipeline(List<Chunker> chunkers) {
        for (Chunker c : chunkers) {
            chunkerMap.put(c.getStrategy(), c);
        }
    }

    public List<DocumentChunk> chunk(ParsedDocument document, ChunkingStrategy strategy, int targetSize) {
        ChunkingStrategy activeStrategy = (strategy != null) ? strategy : ChunkingStrategy.SEMANTIC_PARAGRAPH;
        Chunker chunker = chunkerMap.get(activeStrategy);
        if (chunker == null) {
            chunker = chunkerMap.get(ChunkingStrategy.SEMANTIC_PARAGRAPH);
        }
        return chunker.chunk(document, targetSize);
    }
}

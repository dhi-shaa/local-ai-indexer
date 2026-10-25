package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline Fixed-Size Chunker.
 * Partitions text into rigid token/word blocks regardless of sentence or paragraph semantics.
 * Serves as comparative baseline in the evaluation harness.
 */
@Component
public class FixedSizeChunker implements Chunker {

    @Override
    public ChunkingStrategy getStrategy() {
        return ChunkingStrategy.FIXED_SIZE;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document, int targetSize) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int wordLimit = Math.max(50, targetSize <= 0 ? 150 : targetSize);

        for (ParsedDocument.ParsedPage page : document.getPages()) {
            String pageText = page.getText();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            String[] words = pageText.trim().split("\\s+");
            for (int i = 0; i < words.length; i += wordLimit) {
                int end = Math.min(i + wordLimit, words.length);
                StringBuilder sb = new StringBuilder();
                for (int j = i; j < end; j++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(words[j]);
                }

                String chunkId = document.getDocumentId() + "_fx_p" + page.getPageNumber() + "_c" + (chunkIndex++);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(chunkId);
                chunk.setDocumentId(document.getDocumentId());
                chunk.setFilePath(document.getFilePath());
                chunk.setDocumentName(document.getFileName());
                chunk.setPageNumber(page.getPageNumber());
                chunk.setChunkIndex(chunkIndex);
                chunk.setContent(sb.toString().trim());
                chunks.add(chunk);
            }
        }

        return chunks;
    }
}

package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Semantic Sentence Window Chunker.
 * Groups coherent sentences with configurable overlap to preserve contextual continuity.
 */
@Component
public class SemanticSentenceWindowChunker implements Chunker {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9\"])");

    @Override
    public ChunkingStrategy getStrategy() {
        return ChunkingStrategy.SEMANTIC_SENTENCE_WINDOW;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document, int targetSize) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int windowSize = Math.max(3, targetSize / 100);
        int overlap = 1;

        for (ParsedDocument.ParsedPage page : document.getPages()) {
            String pageText = page.getText();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            String[] rawSentences = SENTENCE_SPLIT.split(pageText.replaceAll("\\s+", " "));
            List<String> sentences = new ArrayList<>();
            for (String s : rawSentences) {
                String clean = s.trim();
                if (!clean.isEmpty()) {
                    sentences.add(clean);
                }
            }

            int step = Math.max(1, windowSize - overlap);
            for (int i = 0; i < sentences.size(); i += step) {
                int end = Math.min(i + windowSize, sentences.size());
                StringBuilder chunkContent = new StringBuilder();
                for (int j = i; j < end; j++) {
                    if (chunkContent.length() > 0) {
                        chunkContent.append(" ");
                    }
                    chunkContent.append(sentences.get(j));
                }

                String chunkId = document.getDocumentId() + "_sw_p" + page.getPageNumber() + "_c" + (chunkIndex++);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(chunkId);
                chunk.setDocumentId(document.getDocumentId());
                chunk.setFilePath(document.getFilePath());
                chunk.setDocumentName(document.getFileName());
                chunk.setPageNumber(page.getPageNumber());
                chunk.setChunkIndex(chunkIndex);
                chunk.setContent(chunkContent.toString().trim());
                chunks.add(chunk);

                if (end >= sentences.size()) {
                    break;
                }
            }
        }

        return chunks;
    }
}

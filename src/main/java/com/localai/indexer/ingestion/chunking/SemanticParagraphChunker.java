package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Semantic Paragraph Chunker.
 * Splits text primarily along natural paragraph boundaries and secondarily along logical sentence boundaries.
 * Preserves thematic integrity and ensures no sentence is ever broken across chunks.
 */
@Component
public class SemanticParagraphChunker implements Chunker {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("(\\r?\\n\\s*){2,}");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9\"])");

    @Override
    public ChunkingStrategy getStrategy() {
        return ChunkingStrategy.SEMANTIC_PARAGRAPH;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document, int targetSize) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int targetCharSize = targetSize <= 0 ? 1200 : targetSize * 3; // Approx 3-4 chars per word

        for (ParsedDocument.ParsedPage page : document.getPages()) {
            String pageText = page.getText();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            String[] paragraphs = PARAGRAPH_SPLIT.split(pageText);
            StringBuilder currentChunkText = new StringBuilder();

            for (String rawPara : paragraphs) {
                String para = rawPara.trim().replaceAll("\\s+", " ");
                if (para.isEmpty()) continue;

                // If paragraph itself exceeds target size, split by logical sentences
                if (para.length() > targetCharSize) {
                    if (currentChunkText.length() > 0) {
                        chunks.add(createChunk(document, page.getPageNumber(), chunkIndex++, currentChunkText.toString().trim()));
                        currentChunkText.setLength(0);
                    }

                    String[] sentences = SENTENCE_SPLIT.split(para);
                    StringBuilder sentenceBuffer = new StringBuilder();

                    for (String sentence : sentences) {
                        String s = sentence.trim();
                        if (s.isEmpty()) continue;

                        if (sentenceBuffer.length() + s.length() + 1 > targetCharSize && sentenceBuffer.length() > 0) {
                            chunks.add(createChunk(document, page.getPageNumber(), chunkIndex++, sentenceBuffer.toString().trim()));
                            sentenceBuffer.setLength(0);
                        }

                        if (sentenceBuffer.length() > 0) {
                            sentenceBuffer.append(" ");
                        }
                        sentenceBuffer.append(s);
                    }

                    if (sentenceBuffer.length() > 0) {
                        chunks.add(createChunk(document, page.getPageNumber(), chunkIndex++, sentenceBuffer.toString().trim()));
                    }
                } else {
                    // Check if adding this paragraph exceeds target size
                    if (currentChunkText.length() + para.length() + 2 > targetCharSize && currentChunkText.length() > 0) {
                        chunks.add(createChunk(document, page.getPageNumber(), chunkIndex++, currentChunkText.toString().trim()));
                        currentChunkText.setLength(0);
                    }

                    if (currentChunkText.length() > 0) {
                        currentChunkText.append("\n\n");
                    }
                    currentChunkText.append(para);
                }
            }

            if (currentChunkText.length() > 0) {
                chunks.add(createChunk(document, page.getPageNumber(), chunkIndex++, currentChunkText.toString().trim()));
                currentChunkText.setLength(0);
            }
        }

        return chunks;
    }

    private DocumentChunk createChunk(ParsedDocument doc, int pageNumber, int index, String content) {
        String chunkId = doc.getDocumentId() + "_p" + pageNumber + "_c" + index;
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(chunkId);
        chunk.setDocumentId(doc.getDocumentId());
        chunk.setFilePath(doc.getFilePath());
        chunk.setDocumentName(doc.getFileName());
        chunk.setPageNumber(pageNumber);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }
}

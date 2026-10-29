package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic Paragraph Chunker.
 * Splits text primarily along natural paragraph boundaries and secondarily along logical sentence boundaries.
 * Preserves thematic integrity, protects abbreviations/initials, handles slide decks (1 per slide),
 * splits code on function/class boundaries, merges short fragments, and records page ranges.
 */
@Component
public class SemanticParagraphChunker implements Chunker {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("(\\r?\\n\\s*){2,}");
    private static final int MIN_CHUNK_CHARS = 120;

    // Common abbreviations that should NOT trigger a sentence split
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "vs", "etc", "eg", "ie", "e.g", "i.e",
            "fig", "figs", "al", "rfc", "no", "vol", "p", "pp", "dept", "univ", "inc", "ltd", "corp"
    ));

    @Override
    public ChunkingStrategy getStrategy() {
        return ChunkingStrategy.SEMANTIC_PARAGRAPH;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document, int targetSize) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int targetCharSize = targetSize <= 0 ? 1200 : targetSize * 3; // Approx 3-4 chars per word

        if (document.getPages().isEmpty()) {
            return chunks;
        }

        // Special handling for slide-style presentation PDFs (short pages with slide footers)
        if (isSlideDeck(document)) {
            for (ParsedDocument.ParsedPage page : document.getPages()) {
                String pageText = page.getText();
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                String trimmed = pageText.trim();
                String title = extractSlideTitle(trimmed);
                String content = trimmed;

                // Prepend slide title to ensure context is prominent
                if (!content.startsWith("[Slide:")) {
                    content = "[Slide: " + title + "]\n\n" + content;
                }

                DocumentChunk chunk = createChunk(document, page.getPageNumber(), page.getPageNumber(), chunkIndex++, content);
                if (isReferencePage(title, trimmed)) {
                    chunk.getMetadata().put("isReference", true);
                }
                chunks.add(chunk);
            }
            return chunks;
        }

        // Standard document processing (articles, reports, code)
        StringBuilder pendingChunk = new StringBuilder();
        int pendingStartPage = -1;
        int pendingEndPage = -1;

        for (ParsedDocument.ParsedPage page : document.getPages()) {
            String pageText = page.getText();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            int currentPageNum = page.getPageNumber();

            // Detect if this page contains code without standard blank-line paragraphs
            List<String> sections = splitPageIntoSections(pageText, targetCharSize);

            for (String section : sections) {
                String cleanSection = section.trim();
                if (cleanSection.isEmpty()) continue;

                // If section itself exceeds target size, split by logical sentences/lines
                if (cleanSection.length() > targetCharSize) {
                    // Flush any pending small chunk
                    if (pendingChunk.length() > 0) {
                        chunks.add(createChunk(document, pendingStartPage, pendingEndPage, chunkIndex++, pendingChunk.toString().trim()));
                        pendingChunk.setLength(0);
                        pendingStartPage = -1;
                    }

                    List<String> units = isCodeBlock(cleanSection) 
                            ? splitCodeBlock(cleanSection, targetCharSize) 
                            : splitIntoSentences(cleanSection);

                    StringBuilder unitBuffer = new StringBuilder();
                    for (String unit : units) {
                        String u = unit.trim();
                        if (u.isEmpty()) continue;

                        if (unitBuffer.length() + u.length() + 1 > targetCharSize && unitBuffer.length() > 0) {
                            // If current unitBuffer is large enough, emit
                            if (unitBuffer.length() >= MIN_CHUNK_CHARS) {
                                chunks.add(createChunk(document, currentPageNum, currentPageNum, chunkIndex++, unitBuffer.toString().trim()));
                                unitBuffer.setLength(0);
                            }
                        }

                        if (unitBuffer.length() > 0) {
                            unitBuffer.append(isCodeBlock(cleanSection) ? "\n" : " ");
                        }
                        unitBuffer.append(u);
                    }

                    if (unitBuffer.length() > 0) {
                        if (unitBuffer.length() < MIN_CHUNK_CHARS && !chunks.isEmpty()) {
                            // Merge into previous chunk if too small
                            DocumentChunk prev = chunks.get(chunks.size() - 1);
                            prev.setContent(prev.getContent() + "\n\n" + unitBuffer.toString().trim());
                            prev.setEndPageNumber(currentPageNum);
                        } else {
                            chunks.add(createChunk(document, currentPageNum, currentPageNum, chunkIndex++, unitBuffer.toString().trim()));
                        }
                    }

                } else {
                    // Normal section
                    if (pendingChunk.length() + cleanSection.length() + 2 > targetCharSize && pendingChunk.length() >= MIN_CHUNK_CHARS) {
                        chunks.add(createChunk(document, pendingStartPage, pendingEndPage, chunkIndex++, pendingChunk.toString().trim()));
                        pendingChunk.setLength(0);
                        pendingStartPage = -1;
                    }

                    if (pendingChunk.length() == 0) {
                        pendingStartPage = currentPageNum;
                    }
                    pendingEndPage = currentPageNum;

                    if (pendingChunk.length() > 0) {
                        pendingChunk.append("\n\n");
                    }
                    pendingChunk.append(cleanSection);
                }
            }
        }

        if (pendingChunk.length() > 0) {
            if (pendingChunk.length() < MIN_CHUNK_CHARS && !chunks.isEmpty()) {
                DocumentChunk prev = chunks.get(chunks.size() - 1);
                prev.setContent(prev.getContent() + "\n\n" + pendingChunk.toString().trim());
                prev.setEndPageNumber(pendingEndPage);
            } else {
                chunks.add(createChunk(document, pendingStartPage, pendingEndPage, chunkIndex++, pendingChunk.toString().trim()));
            }
        }

        return chunks;
    }

    /**
     * Splits text into sentences while strictly protecting initials (e.g. B. C. Neuman)
     * and common abbreviations (e.g. Dr., Fig., e.g., i.e., et al., RFC).
     */
    public static List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }

        StringBuilder currentSentence = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            currentSentence.append(c);

            if ((c == '.' || c == '!' || c == '?') && i + 1 < len) {
                // Peek ahead to see if followed by whitespace and uppercase/number
                int nextIndex = i + 1;
                while (nextIndex < len && Character.isWhitespace(text.charAt(nextIndex))) {
                    nextIndex++;
                }

                if (nextIndex > i + 1 && nextIndex < len) {
                    char nextChar = text.charAt(nextIndex);
                    if (Character.isUpperCase(nextChar) || Character.isDigit(nextChar) || nextChar == '"' || nextChar == '\'') {
                        // Check if this period is part of an initial or abbreviation
                        if (c == '.') {
                            String precedingWord = extractPrecedingWord(text, i);
                            if (isInitialOrAbbreviation(precedingWord)) {
                                // Do NOT split
                                continue;
                            }
                        }

                        // Valid sentence break
                        String s = currentSentence.toString().trim();
                        if (!s.isEmpty()) {
                            sentences.add(s);
                        }
                        currentSentence.setLength(0);
                        i = nextIndex - 1; // Advance to right before next character
                    }
                }
            }
        }

        String remaining = currentSentence.toString().trim();
        if (!remaining.isEmpty()) {
            sentences.add(remaining);
        }

        return sentences;
    }

    private static String extractPrecedingWord(String text, int punctuationIndex) {
        int start = punctuationIndex - 1;
        while (start >= 0 && (Character.isLetterOrDigit(text.charAt(start)) || text.charAt(start) == '.')) {
            start--;
        }
        return text.substring(start + 1, punctuationIndex).trim();
    }

    private static boolean isInitialOrAbbreviation(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        // Single letter initial (e.g. "B", "C", "T")
        if (word.length() == 1 && Character.isLetter(word.charAt(0))) {
            return true;
        }
        String lower = word.toLowerCase();
        if (ABBREVIATIONS.contains(lower)) {
            return true;
        }
        // Multi-part initials like "e.g" or "i.e"
        if (lower.equals("e.g") || lower.equals("i.e")) {
            return true;
        }
        return false;
    }

    private boolean isSlideDeck(ParsedDocument document) {
        int totalPages = document.getPages().size();
        if (totalPages <= 1) return false;

        int slideIndicators = 0;
        for (ParsedDocument.ParsedPage page : document.getPages()) {
            String text = page.getText();
            if (text == null) continue;
            // Slide footer pattern like "9 / 11" or short concise page text
            if (text.matches("(?s).*\\b\\d+\\s*/\\s*\\d+\\b.*") || text.trim().length() < 1300) {
                slideIndicators++;
            }
        }
        return ((double) slideIndicators / totalPages) >= 0.6;
    }

    private String extractSlideTitle(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.matches(".*\\d+\\s*/\\s*\\d+.*")) {
                return trimmed;
            }
        }
        return "Slide";
    }

    private boolean isReferencePage(String title, String content) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("reference") || lowerTitle.contains("bibliography") || lowerTitle.contains("works cited")) {
            return true;
        }
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("references\n") || lowerContent.contains("bibliography\n");
    }

    private List<String> splitPageIntoSections(String pageText, int targetCharSize) {
        // If text contains Python / code defs or class headers, split on def/class boundaries
        if (isCodeBlock(pageText)) {
            String[] defSplits = pageText.split("(?<=\\n)(?=(?:def |class |# ---|# ===))");
            if (defSplits.length > 1) {
                return Arrays.asList(defSplits);
            }
        }

        // Standard paragraph splitting
        String[] paras = PARAGRAPH_SPLIT.split(pageText);
        return Arrays.asList(paras);
    }

    private boolean isCodeBlock(String text) {
        return text.contains("def ") || text.contains("class ") || text.contains("import ") ||
               text.contains("np.") || text.contains("plt.") || text.contains("return ") ||
               text.contains("self.") || text.contains("def fit(") || text.contains("def predict(");
    }

    private List<String> splitCodeBlock(String code, int targetCharSize) {
        List<String> units = new ArrayList<>();
        String[] lines = code.split("\\r?\\n");
        StringBuilder currentUnit = new StringBuilder();

        for (String line : lines) {
            if (currentUnit.length() + line.length() + 1 > targetCharSize && currentUnit.length() > 0) {
                units.add(currentUnit.toString());
                currentUnit.setLength(0);
            }
            if (currentUnit.length() > 0) {
                currentUnit.append("\n");
            }
            currentUnit.append(line);
        }

        if (currentUnit.length() > 0) {
            units.add(currentUnit.toString());
        }
        return units;
    }

    private DocumentChunk createChunk(ParsedDocument doc, int startPage, int endPage, int index, String content) {
        int pageNumber = startPage > 0 ? startPage : 1;
        int endPageNum = endPage > 0 ? endPage : pageNumber;
        String chunkId = doc.getDocumentId() + "_p" + pageNumber + "_c" + index;

        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(chunkId);
        chunk.setDocumentId(doc.getDocumentId());
        chunk.setFilePath(doc.getFilePath());
        chunk.setDocumentName(doc.getFileName());
        chunk.setPageNumber(pageNumber);
        chunk.setStartPageNumber(pageNumber);
        chunk.setEndPageNumber(endPageNum);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }
}

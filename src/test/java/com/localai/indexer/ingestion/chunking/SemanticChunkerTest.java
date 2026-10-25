package com.localai.indexer.ingestion.chunking;

import com.localai.indexer.ingestion.model.DocumentChunk;
import com.localai.indexer.ingestion.model.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticChunkerTest {

    private final SemanticParagraphChunker paragraphChunker = new SemanticParagraphChunker();
    private final SemanticSentenceWindowChunker sentenceChunker = new SemanticSentenceWindowChunker();
    private final FixedSizeChunker fixedChunker = new FixedSizeChunker();

    @Test
    void testSemanticParagraphChunkerPreservesParagraphs() {
        ParsedDocument doc = new ParsedDocument("d1", "sample.txt", "/tmp/sample.txt");
        doc.getPages().add(new ParsedDocument.ParsedPage(1,
                "First paragraph introduces the concept. It has two sentences.\n\n" +
                "Second paragraph elaborates on vector storage. It details H2 extensions."));

        List<DocumentChunk> chunks = paragraphChunker.chunk(doc, 100);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getContent()).contains("First paragraph introduces");
        assertThat(chunks.get(0).getPageNumber()).isEqualTo(1);
    }

    @Test
    void testSemanticSentenceWindowChunkerCreatesOverlappingWindows() {
        ParsedDocument doc = new ParsedDocument("d2", "sample.txt", "/tmp/sample.txt");
        doc.getPages().add(new ParsedDocument.ParsedPage(1,
                "Sentence one is here. Sentence two follows it. Sentence three continues. Sentence four concludes."));

        List<DocumentChunk> chunks = sentenceChunker.chunk(doc, 200);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getContent()).contains("Sentence one");
    }

    @Test
    void testFixedSizeChunkerSplitsByLimit() {
        ParsedDocument doc = new ParsedDocument("d3", "sample.txt", "/tmp/sample.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("word").append(i).append(" ");
        }
        doc.getPages().add(new ParsedDocument.ParsedPage(1, sb.toString()));

        List<DocumentChunk> chunks = fixedChunker.chunk(doc, 50);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
    }
}

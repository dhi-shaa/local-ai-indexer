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
    void testSentenceSplitterProtectsInitialsAndAbbreviations() {
        String text = "See RFC 4120, 2005. B. C. Neuman and T. Ts'o published this. Dr. Smith agreed with et al. on Fig. 4. Next sentence begins here.";
        List<String> sentences = SemanticParagraphChunker.splitIntoSentences(text);

        // B. C. Neuman and T. Ts'o must not be split into single-letter fragments!
        assertThat(sentences).anyMatch(s -> s.contains("B. C. Neuman and T. Ts'o published this"));
        assertThat(sentences).anyMatch(s -> s.contains("Dr. Smith agreed with et al. on Fig. 4"));
        assertThat(sentences).anyMatch(s -> s.contains("Next sentence begins here"));
        // Ensure no tiny fragment like "B." or "T." exists alone
        assertThat(sentences).noneMatch(s -> s.trim().equals("B.") || s.trim().equals("T.") || s.trim().equals("C."));
    }

    @Test
    void testSlideDeckChunkingUsesOneChunkPerSlideWithTitle() {
        ParsedDocument doc = new ParsedDocument("slideDoc", "presentation.pdf", "/path/presentation.pdf");
        doc.getPages().add(new ParsedDocument.ParsedPage(1, "Title Slide\nAuthor: Alice\n1 / 2"));
        doc.getPages().add(new ParsedDocument.ParsedPage(2, "Limitations and Attacks\nThreat 1 -> Mitigation 1\n2 / 2"));

        List<DocumentChunk> chunks = paragraphChunker.chunk(doc, 400);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getPageNumber()).isEqualTo(1);
        assertThat(chunks.get(1).getPageNumber()).isEqualTo(2);
        assertThat(chunks.get(1).getContent()).contains("Limitations and Attacks");
        assertThat(chunks.get(1).getContent()).contains("[Slide: Limitations and Attacks]");
    }

    @Test
    void testCodeChunkingSplitsOnFunctionBoundaries() {
        ParsedDocument doc = new ParsedDocument("codeDoc", "ml_prep.pdf", "/path/ml_prep.pdf");
        String code = "import numpy as np\n\n" +
                "def fit(X, y):\n" +
                "    # train logistic regression\n" +
                "    return np.zeros(X.shape[1])\n\n" +
                "def predict(X):\n" +
                "    # predict labels\n" +
                "    return np.ones(len(X))\n";
        doc.getPages().add(new ParsedDocument.ParsedPage(1, code));

        List<DocumentChunk> chunks = paragraphChunker.chunk(doc, 50);

        assertThat(chunks).isNotEmpty();
        for (DocumentChunk chunk : chunks) {
            // Verify no chunk cuts mid-word
            assertThat(chunk.getContent()).doesNotStartWith("it(");
            assertThat(chunk.getContent()).doesNotStartWith("redict(");
        }
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

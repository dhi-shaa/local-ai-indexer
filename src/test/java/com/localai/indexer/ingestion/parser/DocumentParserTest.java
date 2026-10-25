package com.localai.indexer.ingestion.parser;

import com.localai.indexer.ingestion.model.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    void testParsePlainTextStream() throws Exception {
        String content = "Hello World. This is a private indexing engine test.\n\nSecond paragraph.";
        ByteArrayInputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ParsedDocument doc = parser.parse(is, "test.txt", "/tmp/test.txt");

        assertThat(doc).isNotNull();
        assertThat(doc.getFileName()).isEqualTo("test.txt");
        assertThat(doc.getFullText()).contains("private indexing engine test");
        assertThat(doc.getPages()).hasSize(1);
    }

    @Test
    void testParseMultiPageFormFeed() throws Exception {
        String content = "Page 1 Content\fPage 2 Content\fPage 3 Content";
        ByteArrayInputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ParsedDocument doc = parser.parse(is, "doc.txt", "/tmp/doc.txt");

        assertThat(doc.getPages()).hasSize(3);
        assertThat(doc.getPages().get(0).getPageNumber()).isEqualTo(1);
        assertThat(doc.getPages().get(0).getText()).isEqualTo("Page 1 Content");
        assertThat(doc.getPages().get(1).getPageNumber()).isEqualTo(2);
        assertThat(doc.getPages().get(1).getText()).isEqualTo("Page 2 Content");
    }
}

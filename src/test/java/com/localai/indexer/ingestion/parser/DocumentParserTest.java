package com.localai.indexer.ingestion.parser;

import com.localai.indexer.ingestion.model.ParsedDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        System.out.println("FULL TEXT CODES: ");
        for (char c : doc.getFullText().toCharArray()) {
            if (c < 32 || c > 126) {
                System.out.println("Char: " + (int) c + " hex=" + Integer.toHexString(c));
            }
        }
        System.out.println("DOC.TXT PAGES: " + doc.getPages().size() + " FULLTEXT: [" + doc.getFullText() + "]");
    }

    @Test
    void testInspectKerberosPdf() throws Exception {
        java.io.File file = new java.io.File("C:\\Users\\delld\\Downloads\\kerberos_authentication.pdf");
        if (file.exists()) {
            ParsedDocument doc = parser.parse(file);
            assertThat(doc.getPages()).hasSize(11);
            assertThat(doc.getPages().get(8).getPageNumber()).isEqualTo(9);
            assertThat(doc.getPages().get(8).getText()).contains("Limitations and Attacks");
            assertThat(doc.getPages().get(8).getText()).contains("Threat: KDC is a single point of failure");
            assertThat(doc.getPages().get(8).getText()).contains("Mitigation: Replicated KDCs");
            assertThat(doc.getPages().get(8).getText()).contains("Threat: Offline password guessing (AS-REP roasting)");
            assertThat(doc.getPages().get(8).getText()).contains("Threat: Kerberoasting");
        }
    }
}

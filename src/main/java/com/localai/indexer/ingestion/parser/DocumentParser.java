package com.localai.indexer.ingestion.parser;

import com.localai.indexer.ingestion.model.ParsedDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * Apache Tika document parser supporting PDF, TXT, DOCX, Markdown, etc.
 * Extracts raw textual content, document metadata, and preserves page boundaries.
 */
@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);
    private final Parser parser = new AutoDetectParser();

    /**
     * Parse file from disk.
     */
    public ParsedDocument parse(File file) throws Exception {
        if (!file.exists() || !file.canRead()) {
            throw new IllegalArgumentException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        try (InputStream is = new FileInputStream(file)) {
            return parse(is, file.getName(), file.getAbsolutePath());
        }
    }

    /**
     * Parse input stream with file context.
     */
    public ParsedDocument parse(InputStream inputStream, String fileName, String filePath) throws Exception {
        log.info("Parsing document with Apache Tika: {}", fileName);

        java.io.StringWriter writer = new java.io.StringWriter();
        org.apache.tika.sax.WriteOutContentHandler handler = new org.apache.tika.sax.WriteOutContentHandler(writer, -1);
        Metadata tikaMetadata = new Metadata();
        ParseContext parseContext = new ParseContext();

        parser.parse(inputStream, handler, tikaMetadata, parseContext);

        String fullText = writer.toString();
        log.info("EXTRACTED TEXT REPR: {}", fullText.replace("\n", "\\n").replace("\r", "\\r").replace("\f", "\\f"));
        String documentId = UUID.nameUUIDFromBytes((filePath != null ? filePath : fileName).getBytes()).toString();

        ParsedDocument parsedDoc = new ParsedDocument(documentId, fileName, filePath != null ? filePath : fileName);
        parsedDoc.setFullText(fullText);

        // Populate metadata
        for (String name : tikaMetadata.names()) {
            parsedDoc.getMetadata().put(name, tikaMetadata.get(name));
        }
        parsedDoc.setContentType(tikaMetadata.get("Content-Type"));

        // Extract pages using form feed delimiters (\u000c / \f) standard in Tika PDF extraction
        extractPages(parsedDoc, fullText);

        log.info("Successfully parsed '{}' ({} characters, {} logical pages)",
                fileName, fullText.length(), parsedDoc.getPages().size());
        return parsedDoc;
    }

    private void extractPages(ParsedDocument doc, String fullText) {
        if (fullText == null || fullText.isBlank()) {
            doc.getPages().add(new ParsedDocument.ParsedPage(1, ""));
            return;
        }

        // Tika embeds form-feed character \f (\u000c) for PDF page boundaries
        String[] rawPages = fullText.split("[\\f\\u000c\\uFFFD]");
        if (rawPages.length > 1) {
            int pageNum = 1;
            for (String p : rawPages) {
                String clean = p.trim();
                if (!clean.isEmpty()) {
                    doc.getPages().add(new ParsedDocument.ParsedPage(pageNum++, clean));
                }
            }
        }

        // If no form-feed breaks were found (e.g. TXT, markdown, single-page PDF)
        if (doc.getPages().isEmpty()) {
            doc.getPages().add(new ParsedDocument.ParsedPage(1, fullText.trim()));
        }
    }
}

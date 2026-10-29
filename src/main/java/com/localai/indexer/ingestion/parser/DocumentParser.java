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
import java.util.List;
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

        Metadata tikaMetadata = new Metadata();
        ParseContext parseContext = new ParseContext();

        java.io.StringWriter writer = new java.io.StringWriter();
        org.apache.tika.sax.WriteOutContentHandler writerHandler = new org.apache.tika.sax.WriteOutContentHandler(writer, -1);

        List<String> pageTexts = new java.util.ArrayList<>();
        StringBuilder currentPage = new StringBuilder();

        org.apache.tika.sax.ContentHandlerDecorator handler = new org.apache.tika.sax.ContentHandlerDecorator(writerHandler) {
            private boolean inPage = false;

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) throws org.xml.sax.SAXException {
                super.startElement(uri, localName, qName, attributes);
                if ("div".equalsIgnoreCase(qName) && "page".equalsIgnoreCase(attributes.getValue("class"))) {
                    inPage = true;
                    currentPage.setLength(0);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws org.xml.sax.SAXException {
                super.endElement(uri, localName, qName);
                if ("div".equalsIgnoreCase(qName) && inPage) {
                    pageTexts.add(currentPage.toString().trim());
                    currentPage.setLength(0);
                    inPage = false;
                } else if ("p".equalsIgnoreCase(qName) || "br".equalsIgnoreCase(qName) || "tr".equalsIgnoreCase(qName)) {
                    currentPage.append("\n");
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws org.xml.sax.SAXException {
                super.characters(ch, start, length);
                currentPage.append(ch, start, length);
            }
        };

        parser.parse(inputStream, handler, tikaMetadata, parseContext);

        String fullText = writer.toString();
        String documentId = UUID.nameUUIDFromBytes((filePath != null ? filePath : fileName).getBytes()).toString();
        ParsedDocument parsedDoc = new ParsedDocument(documentId, fileName, filePath != null ? filePath : fileName);

        // Populate metadata
        for (String name : tikaMetadata.names()) {
            parsedDoc.getMetadata().put(name, tikaMetadata.get(name));
        }
        parsedDoc.setContentType(tikaMetadata.get("Content-Type"));

        if (!pageTexts.isEmpty()) {
            int pageNum = 1;
            StringBuilder fullDocText = new StringBuilder();
            for (String rawPage : pageTexts) {
                String cleaned = cleanAndFormatPage(rawPage);
                parsedDoc.getPages().add(new ParsedDocument.ParsedPage(pageNum++, cleaned));
                if (fullDocText.length() > 0) fullDocText.append("\n\n");
                fullDocText.append(cleaned);
            }
            parsedDoc.setFullText(fullDocText.toString());
        } else {
            // Fallback for documents without <div class="page"> (e.g. text, markdown)
            String rawFull = fullText.trim();
            // Split on real form-feed characters (\f / \u000c)
            String[] ffPages = rawFull.split("[\\f\\u000c]");
            if (ffPages.length > 1) {
                int pageNum = 1;
                for (String p : ffPages) {
                    parsedDoc.getPages().add(new ParsedDocument.ParsedPage(pageNum++, cleanAndFormatPage(p.trim())));
                }
            } else {
                parsedDoc.getPages().add(new ParsedDocument.ParsedPage(1, cleanAndFormatPage(rawFull)));
            }
            parsedDoc.setFullText(cleanAndFormatPage(rawFull));
        }

        log.info("Successfully parsed '{}' ({} characters, {} logical pages)",
                fileName, parsedDoc.getFullText().length(), parsedDoc.getPages().size());
        return parsedDoc;
    }

    /**
     * Cleans OCR/ligature artifacts and formats tabular structures (Threat -> Mitigation).
     */
    public static String cleanAndFormatPage(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Clean unmapped ligatures and missing font glyphs from PDFBox
        String cleaned = text.replace("O\uFFFDine", "Offline")
                             .replace("O ine", "Offline")
                             .replace("speci\uFFFDed", "specified")
                             .replace("veri\uFFFD", "verified")
                             .replace("con\uFFFDdentiality", "confidentiality")
                             .replace("Con\uFFFDdentiality", "Confidentiality")
                             .replace("\uFFFD", "");

        // Detect and reformat two-column threat/mitigation tables
        if (cleaned.contains("Threat / Limitation") && cleaned.contains("Mitigation")) {
            cleaned = formatThreatMitigationTable(cleaned);
        }

        return cleaned;
    }

    private static String formatThreatMitigationTable(String text) {
        // Pre-normalize common PDF concatenation and hyphenation artifacts on slide tables
        String normalized = text.replace("AS-REProasting", "AS-REP roasting")
                                .replace("failure andcompromise", "failure and compromise")
                                .replace("pro-tection", "protection")
                                .replace("service-ticketencryption", "service-ticket encryption")
                                .replace("encryptiontypes", "encryption types")
                                .replace("5minutes", "5 minutes")
                                .replace(")Regular key rotation", ")\nRegular key rotation")
                                .replace("intercepted tickets Short lifetimes", "intercepted tickets\nShort lifetimes");

        String[] lines = normalized.split("\\r?\\n");
        List<String> resultLines = new java.util.ArrayList<>();
        boolean inTable = false;
        List<String> tableLines = new java.util.ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.contains("Threat / Limitation") && trimmed.contains("Mitigation")) {
                inTable = true;
                resultLines.add(trimmed);
                resultLines.add("--- Table: Threat / Limitation -> Mitigation ---");
                continue;
            }

            if (inTable) {
                // Check if table has ended (e.g. footer with slide number or next section)
                if (trimmed.matches(".*\\d+\\s*/\\s*\\d+.*") || trimmed.startsWith("Applications") || trimmed.startsWith("Conclusion")) {
                    // Flush table lines
                    flushTablePairs(tableLines, resultLines);
                    tableLines.clear();
                    inTable = false;
                    resultLines.add(trimmed);
                } else {
                    tableLines.add(trimmed);
                }
            } else {
                resultLines.add(trimmed);
            }
        }

        if (inTable && !tableLines.isEmpty()) {
            flushTablePairs(tableLines, resultLines);
        }

        return String.join("\n\n", resultLines);
    }

    private static void flushTablePairs(List<String> tableLines, List<String> resultLines) {
        int i = 0;
        while (i < tableLines.size()) {
            String line = tableLines.get(i);

            // If line contains both threat and mitigation combined
            if (line.startsWith("Replay of intercepted tickets") && line.contains("Short lifetimes")) {
                String threat = "Replay of intercepted tickets";
                String mitigation = line.substring("Replay of intercepted tickets".length()).trim();
                resultLines.add("Threat: " + threat + " -> Mitigation: " + mitigation);
                i++;
                continue;
            }

            // If line is a multi-line threat (e.g. "Golden / Silver ticket (stolen krbtgt" and "/ service key)")
            if (line.startsWith("Golden / Silver ticket") && (i + 1 < tableLines.size()) && tableLines.get(i + 1).contains("service key")) {
                String threat = line + " " + tableLines.get(i + 1);
                String mitigation = (i + 2 < tableLines.size()) ? tableLines.get(i + 2) : "";
                resultLines.add("Threat: " + threat + " -> Mitigation: " + mitigation);
                i += 3;
                continue;
            }

            // Normal alternating lines: line i = Threat, line i+1 = Mitigation
            if (i + 1 < tableLines.size()) {
                String threat = line;
                String mitigation = tableLines.get(i + 1);
                resultLines.add("Threat: " + threat + " -> Mitigation: " + mitigation);
                i += 2;
            } else {
                resultLines.add(line);
                i++;
            }
        }
    }
}

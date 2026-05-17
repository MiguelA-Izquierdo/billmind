package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class PdfInvoiceParser implements InvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(PdfInvoiceParser.class);

    @Override
    public String extractText(byte[] pdfContent) {
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        try {
            Document document = parser.parse(new ByteArrayInputStream(pdfContent));
            return normalizeFragmented(document.text());
        } catch (BlankDocumentException e) {
            log.warn("PDF contains no extractable text (likely a scanned image)");
            return "";
        }
    }

    /**
     * PDFBox sometimes extracts text with each character or syllable on its own line
     * due to font encoding issues. Joins consecutive short lines (≤3 trimmed chars)
     * without a separator to reconstruct readable tokens.
     */
    private String normalizeFragmented(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            sb.append(trimmed);
            if (i < lines.length - 1) {
                String nextTrimmed = lines[i + 1].trim();
                boolean curShort  = trimmed.length()     <= 3;
                boolean nextShort = nextTrimmed.length() <= 3;
                if (!curShort || !nextShort) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }
}
package dev.izquierdo.billmind._shared.infrastructure.pdf;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    public String extract(byte[] pdfContent) {
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        try {
            Document document = parser.parse(new ByteArrayInputStream(pdfContent));
            return collapseWhitespace(document.text());
        } catch (BlankDocumentException e) {
            log.warn("PDF contains no extractable text (likely a scanned image)");
            return "";
        }
    }

    private String collapseWhitespace(String text) {
        return text.replaceAll("[\\r\\n\\t]+", " ")
                   .replaceAll(" {2,}", " ")
                   .strip();
    }
}

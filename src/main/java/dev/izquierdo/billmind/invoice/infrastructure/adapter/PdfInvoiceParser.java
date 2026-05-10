package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceReference;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PdfInvoiceParser implements InvoiceParser {

    @Override
    public List<InvoiceChunk> parseToChunks(Invoice invoice, byte[] pdfContent) {
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document document = parser.parse(new ByteArrayInputStream(pdfContent));

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(500, 100);
        List<TextSegment> segments = splitter.split(document);

        return segments.stream()
                .map(segment -> {
                    Integer pageNumber = segment.metadata().getInteger("page_number");
                    return new InvoiceChunk(
                        segment.text(),
                        new InvoiceReference(
                                invoice.getId(),
                                pageNumber != null ? pageNumber : 0,
                                "Invoice PDF Section"
                        )
                    );
                })
                .collect(Collectors.toList());
    }
}
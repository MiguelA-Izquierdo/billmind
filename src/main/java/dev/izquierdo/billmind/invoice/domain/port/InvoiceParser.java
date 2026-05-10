package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;

import java.util.List;

public interface InvoiceParser {
    List<InvoiceChunk> parseToChunks(Invoice invoice, byte[] pdfContent);
}
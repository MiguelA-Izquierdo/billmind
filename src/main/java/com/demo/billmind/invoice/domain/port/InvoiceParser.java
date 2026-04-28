package com.demo.billmind.invoice.domain.port;

import com.demo.billmind.invoice.domain.model.Invoice;
import com.demo.billmind.invoice.domain.model.InvoiceChunk;

import java.util.List;

public interface InvoiceParser {
    List<InvoiceChunk> parseToChunks(Invoice invoice, byte[] pdfContent);
}
package com.demo.billmind.invoice.domain.port;

import com.demo.billmind.invoice.domain.model.InvoiceChunk;

import java.util.List;

public interface InvoiceChunkRepository {
    void store(List<InvoiceChunk> chunks);
}

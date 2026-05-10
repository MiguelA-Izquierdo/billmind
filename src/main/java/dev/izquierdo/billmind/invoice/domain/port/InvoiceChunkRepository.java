package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;

import java.util.List;

public interface InvoiceChunkRepository {
    void store(List<InvoiceChunk> chunks);
}

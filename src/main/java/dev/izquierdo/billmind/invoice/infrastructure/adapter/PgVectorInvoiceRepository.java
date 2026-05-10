package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceChunkRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PgVectorInvoiceRepository implements InvoiceChunkRepository {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public PgVectorInvoiceRepository(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void store(List<InvoiceChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;

        List<TextSegment> segments = chunks.stream()
                .map(chunk -> {
                    Metadata metadata = new Metadata();
                    metadata.add("invoice_id", chunk.getReference().getInvoiceId().toString());
                    metadata.add("page_number", chunk.getReference().getPageNumber());
                    metadata.add("section", chunk.getReference().getSection());
                    return TextSegment.from(chunk.getContent(), metadata);
                })
                .collect(Collectors.toList());

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }
}
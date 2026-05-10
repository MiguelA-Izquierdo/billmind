package dev.izquierdo.billmind.invoice.domain.model;

import java.util.Objects;

public class InvoiceChunk {
    private final String content;
    private final InvoiceReference reference;

    public InvoiceChunk(String content, InvoiceReference reference) {
        Objects.requireNonNull(content, "Content cannot be null");
        if (content.isBlank()) throw new IllegalArgumentException("Content cannot be blank");
        this.content = content;
        this.reference = Objects.requireNonNull(reference, "Reference cannot be null");
    }

    public String getContent() {
        return content;
    }

    public InvoiceReference getReference() {
        return reference;
    }
}
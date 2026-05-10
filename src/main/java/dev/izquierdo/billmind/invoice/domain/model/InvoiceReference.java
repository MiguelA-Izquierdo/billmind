package dev.izquierdo.billmind.invoice.domain.model;

import java.util.Objects;
import java.util.UUID;

public class InvoiceReference {
    private final UUID invoiceId;
    private final int pageNumber;
    private final String section;

    public InvoiceReference(UUID invoiceId, int pageNumber, String section) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "Invoice reference cannot be null");
        if (pageNumber < 0) throw new IllegalArgumentException("Page number cannot be negative");
        this.pageNumber = pageNumber;
        Objects.requireNonNull(section, "Section cannot be null");
        if (section.isBlank()) throw new IllegalArgumentException("Section cannot be blank");
        this.section = section;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getSection() {
        return section;
    }
}

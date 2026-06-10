package dev.izquierdo.billmind.invoice.domain.model;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;

import java.util.Objects;

public class InvoiceClassification {

    private final InvoiceType type;
    private final String company;

    public InvoiceClassification(InvoiceType type, String company) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.company = company != null ? company.trim() : "";
    }

    public InvoiceType getType() {
        return type;
    }

    public String getCompany() {
        return company;
    }

    public boolean isSupplyInvoice() {
        return type.isSupply();
    }
}
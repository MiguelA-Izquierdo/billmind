package dev.izquierdo.billmind.invoice.domain.model;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.util.Objects;

public class InvoiceClassification {

    private final SupplyDomain type;
    private final String company;

    public InvoiceClassification(SupplyDomain type, String company) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.company = company != null ? company.trim() : "";
    }

    public SupplyDomain getType() {
        return type;
    }

    public String getCompany() {
        return company;
    }

    public boolean isSupplyInvoice() {
        return type.isSupply();
    }
}
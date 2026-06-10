package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

public interface InvoiceFieldExtractor {
    InvoiceFields extract(String invoiceText, InvoiceType type);
}
package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.model.fields.InvoiceFields;

public interface InvoiceFieldExtractor {
    InvoiceFields extract(String invoiceText, InvoiceType type);
}
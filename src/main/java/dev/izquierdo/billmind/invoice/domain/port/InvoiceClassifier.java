package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;

public interface InvoiceClassifier {
    InvoiceClassification classify(String text);
}

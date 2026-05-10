package dev.izquierdo.billmind.invoice.domain.port;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import org.springframework.stereotype.Service;

@Service
public interface InvoiceClassifier {
    InvoiceClassification classify(byte[] pdfContent);
}

package com.demo.billmind.invoice.domain.port;

import com.demo.billmind.invoice.domain.model.InvoiceClassification;
import org.springframework.stereotype.Service;

@Service
public interface InvoiceClassifier {
    InvoiceClassification classify(byte[] pdfContent);
}

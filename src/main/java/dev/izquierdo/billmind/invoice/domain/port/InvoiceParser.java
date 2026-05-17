package dev.izquierdo.billmind.invoice.domain.port;

public interface InvoiceParser {
    String extractText(byte[] pdfContent);
}
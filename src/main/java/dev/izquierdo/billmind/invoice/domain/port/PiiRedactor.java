package dev.izquierdo.billmind.invoice.domain.port;

public interface PiiRedactor {
    String redact(String text);
}
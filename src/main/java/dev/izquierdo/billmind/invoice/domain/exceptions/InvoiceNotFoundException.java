package dev.izquierdo.billmind.invoice.domain.exceptions;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException() {
        super("Factura no encontrada");
    }
}
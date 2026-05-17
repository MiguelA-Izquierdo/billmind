package dev.izquierdo.billmind.invoice.domain.exceptions;

public class InvoiceFieldExtractionException extends RuntimeException {

    public InvoiceFieldExtractionException(Throwable cause) {
        super("No se ha podido extraer la información de la factura. Por favor, inténtalo de nuevo.", cause);
    }
}
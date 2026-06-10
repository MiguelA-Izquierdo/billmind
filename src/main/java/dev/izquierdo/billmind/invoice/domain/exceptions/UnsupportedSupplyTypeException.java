package dev.izquierdo.billmind.invoice.domain.exceptions;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;

public class UnsupportedSupplyTypeException extends RuntimeException {

    private final InvoiceType type;

    public UnsupportedSupplyTypeException(InvoiceType type) {
        super("El tipo de suministro '" + type.name() + "' no está soportado todavía. Por el momento solo se aceptan facturas de electricidad.");
        this.type = type;
    }

    public InvoiceType getType() {
        return type;
    }
}
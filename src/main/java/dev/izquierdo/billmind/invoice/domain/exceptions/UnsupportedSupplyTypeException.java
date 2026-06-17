package dev.izquierdo.billmind.invoice.domain.exceptions;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

public class UnsupportedSupplyTypeException extends RuntimeException {

    private final SupplyDomain type;

    public UnsupportedSupplyTypeException(SupplyDomain type) {
        super("El tipo de suministro '" + type.name() + "' no está soportado todavía. Por el momento solo se aceptan facturas de electricidad.");
        this.type = type;
    }

    public SupplyDomain getType() {
        return type;
    }
}
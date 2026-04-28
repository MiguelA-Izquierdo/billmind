package com.demo.billmind.invoice.domain.exceptions;

public class NotASupplyInvoiceException extends RuntimeException {

    public NotASupplyInvoiceException() {
        super("El archivo no parece ser una factura de suministro del hogar (electricidad, gas, agua o telecomunicaciones)");
    }
}

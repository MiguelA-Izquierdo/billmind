package dev.izquierdo.billmind._shared.domain.model;

public enum InvoiceType {
    LUZ, AGUA, GAS, TELCO, OTRO;

    public boolean isSupply() {
        return this != OTRO;
    }
}
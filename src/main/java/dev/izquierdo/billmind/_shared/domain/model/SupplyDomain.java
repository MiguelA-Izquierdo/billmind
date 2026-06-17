package dev.izquierdo.billmind._shared.domain.model;

public enum SupplyDomain {
    ELECTRICITY,
    GAS,
    WATER,
    TELECOM,
    OTHER;

    public boolean isSupply() {
        return this != OTHER;
    }
}
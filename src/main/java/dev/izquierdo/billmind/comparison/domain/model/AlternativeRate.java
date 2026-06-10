package dev.izquierdo.billmind.comparison.domain.model;

public sealed interface AlternativeRate
        permits ElectricityAlternativeRate {

    String company();
    String tariffName();
}
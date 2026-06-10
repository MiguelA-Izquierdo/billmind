package dev.izquierdo.billmind.comparison.domain.model;

public sealed interface MarketOffer
        permits ElectricityMarketOffer {

    String company();
    String tariffName();
}
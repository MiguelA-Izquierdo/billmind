package dev.izquierdo.billmind.assistant.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic price comparison for the user's invoice, precomputed by the comparison
 * engine and surfaced to the assistant. It lets the LLM explain a ready-made result
 * (cheapest tariff, effective price, annual savings) instead of ranking raw market
 * tariffs itself — a task LLMs perform unreliably.
 */
public record ComparisonSummary(
    BigDecimal userEffectivePricePerKwh,
    boolean userIsTou,
    BigDecimal annualKwhEstimate,
    OfferBlock flatBlock,
    OfferBlock touBlock
) {
    public record OfferBlock(
        String bestCompany,
        String bestTariffName,
        BigDecimal bestPricePerKwh,
        BigDecimal annualSavingsEuros,
        List<Alternative> alternatives
    ) {}

    public record Alternative(
        String company,
        String tariffName,
        BigDecimal pricePerKwh
    ) {}
}
package dev.izquierdo.billmind.assistant.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic price comparison for the user's invoice, precomputed by the comparison
 * engine and surfaced to the assistant. It lets the LLM explain a ready-made result
 * (cheapest tariff, effective price, annual savings) instead of ranking raw market
 * tariffs itself — a task LLMs perform unreliably.
 *
 * <p>The saving arrives as a band together with the {@link Basis} it was built on, because the
 * model repeats whatever this context says. Handing it a single figure produced a confident
 * "ahorrarás 182,34 € al año" out of a 32-day extrapolation.
 */
public record ComparisonSummary(
    BigDecimal userEffectivePricePerKwh,
    boolean userIsTou,
    BigDecimal annualKwhEstimate,
    BigDecimal invoiceTotalEuros,
    Basis basis,
    OfferBlock flatBlock,
    OfferBlock touBlock
) {
    /** What the figures were built from, so the assistant can state it rather than imply precision. */
    public record Basis(
        long observedDays,
        boolean annualised,
        boolean powerTermIncluded,
        boolean powerTermEstimated,
        boolean consumptionProfileAssumed,
        boolean taxesIncluded
    ) {}

    public record OfferBlock(
        String bestCompany,
        String bestTariffName,
        BigDecimal bestPricePerKwh,
        /** What this very invoice would have cost on the offer — measured, not projected. */
        BigDecimal periodSavingsEuros,
        BigDecimal annualSavingsLow,
        BigDecimal annualSavingsHigh,
        List<Alternative> alternatives
    ) {}

    public record Alternative(
        String company,
        String tariffName,
        BigDecimal pricePerKwh
    ) {}
}
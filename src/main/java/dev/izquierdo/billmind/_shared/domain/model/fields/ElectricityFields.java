package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ElectricityFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        BigDecimal consumptionKwh,
        BigDecimal consumptionKwhP1,
        BigDecimal consumptionKwhP2,
        BigDecimal consumptionKwhP3,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhP1,
        BigDecimal pricePerKwhP2,
        BigDecimal pricePerKwhP3,
        BigDecimal contractedPowerKw,
        BigDecimal powerPriceP1PerKwDay,
        BigDecimal powerPriceP2PerKwDay
) implements InvoiceFields {

    /**
     * (1 + IEE) × (1 + IVA) with the ordinary Spanish rates — IEE 5.1126963 % (Ley 38/1992) and
     * IVA 21 %, both back in force since 01/06/2026 after the temporary reductions of that spring.
     * Used only to project a future saving and to derive a missing power term; the reconciliation
     * below never assumes it, so an invoice billed under the reduced rates still reconciles.
     */
    public static final BigDecimal ORDINARY_TAX_FACTOR = new BigDecimal("1.27186");

    /** Verdict of {@link #reconcileWithTotal()}. */
    public enum TaxBasis {
        /** Extracted prices exclude taxes: the invoice total is the comparable cost plus tax. */
        PRE_TAX,
        /** Extracted prices already carry taxes: applying the tax factor again would inflate them. */
        POST_TAX,
        /** The parts do not add up to the printed total — the extraction cannot be trusted. */
        INCOHERENT,
        /** Not enough extracted fields to add anything up. */
        INSUFFICIENT_DATA
    }

    // Ratio of printed total to comparable cost. Above PRE_TAX_MIN the gap can only be tax; below
    // POST_TAX_MAX the prices already carried it. The bands are wide because the comparable cost
    // omits the fixed lines nobody extracts (bono social, meter rental) and any discount — they are
    // ~1-2 % of a domestic invoice, while a misread price moves the ratio by tens of percent.
    private static final BigDecimal PRE_TAX_MIN  = new BigDecimal("1.15");
    private static final BigDecimal PRE_TAX_MAX  = new BigDecimal("1.40");
    private static final BigDecimal POST_TAX_MIN = new BigDecimal("0.90");

    // Same idea against energy alone, for invoices whose power term was never extracted. Energy is
    // 55-80 % of a domestic comparable cost, so a pre-tax invoice lands well above this threshold
    // and a post-tax one just above 1. Energy alone cannot exceed the printed total, though —
    // below ENERGY_ONLY_MIN some extracted number is wrong.
    private static final BigDecimal ENERGY_ONLY_PRE_TAX_MIN = new BigDecimal("1.20");
    private static final BigDecimal ENERGY_ONLY_MIN         = new BigDecimal("0.95");

    /**
     * Period prices that are all the same are not a time-of-use tariff — they are one price the
     * invoice printed once per period. Deciding that is arithmetic, not judgement, so it does not
     * belong to the model: the extraction prompt states the rule and the model honoured it on one
     * invoice and forgot it on the next, storing 0.122/0.122/0.122 as TOU. Returns {@code this}
     * when the prices genuinely differ, when a flat price is already set, or when only one period
     * carries a price — that last case is a half-read TOU invoice, and failing is better than
     * turning it into a confident flat rate.
     */
    public ElectricityFields withFlatRateIfUniform() {
        if (pricePerKwh != null) return this;
        BigDecimal uniform = uniformPeriodPrice();
        if (uniform == null) return this;
        return new ElectricityFields(billingPeriodStart, billingPeriodEnd, totalAmount,
                consumptionKwh, consumptionKwhP1, consumptionKwhP2, consumptionKwhP3,
                uniform, null, null, null, contractedPowerKw,
                powerPriceP1PerKwDay, powerPriceP2PerKwDay);
    }

    /** The price shared by every period that carries one, or null below two periods or on a mismatch. */
    private BigDecimal uniformPeriodPrice() {
        BigDecimal first = null;
        int found = 0;
        for (BigDecimal price : new BigDecimal[]{pricePerKwhP1, pricePerKwhP2, pricePerKwhP3}) {
            if (price == null) continue;
            found++;
            // compareTo, not equals: 0.122 and 0.1220 are the same price written to a different scale.
            if (first == null) first = price;
            else if (first.compareTo(price) != 0) return null;
        }
        return found >= 2 ? first : null;
    }

    /** Days invoiced, or 0 when either date is missing or the period is inverted. */
    public long billingDays() {
        if (billingPeriodStart == null || billingPeriodEnd == null) return 0;
        long days = ChronoUnit.DAYS.between(billingPeriodStart, billingPeriodEnd);
        return days > 0 ? days : 0;
    }

    /** Energy charged over the billing period, or null when prices and consumption do not pair up. */
    public BigDecimal energyCostForPeriod() {
        if (pricePerKwh != null && consumptionKwh != null) {
            return pricePerKwh.multiply(consumptionKwh);
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal[] prices = {pricePerKwhP1, pricePerKwhP2, pricePerKwhP3};
        BigDecimal[] kwh    = {consumptionKwhP1, consumptionKwhP2, consumptionKwhP3};
        for (int p = 0; p < prices.length; p++) {
            if (prices[p] == null) continue;
            if (kwh[p] == null) return null;   // a priced period with no consumption cannot be summed
            total = total.add(prices[p].multiply(kwh[p]));
        }
        return total.signum() > 0 ? total : null;
    }

    /**
     * Power charged over the billing period, or null when the power term was not extracted.
     * Both 2.0TD periods bill the single contracted power; domestic supplies contract the same
     * kW in punta and valle, which is what {@code contractedPowerKw} holds.
     */
    public BigDecimal powerCostForPeriod() {
        if (contractedPowerKw == null || billingDays() == 0) return null;
        BigDecimal dailyPrice = sumPowerPrices();
        if (dailyPrice == null) return null;
        return contractedPowerKw.multiply(dailyPrice).multiply(BigDecimal.valueOf(billingDays()));
    }

    /** P1 + P2 daily power price, or null when neither period carries one. */
    public BigDecimal sumPowerPrices() {
        if (powerPriceP1PerKwDay == null && powerPriceP2PerKwDay == null) return null;
        BigDecimal p1 = powerPriceP1PerKwDay != null ? powerPriceP1PerKwDay : BigDecimal.ZERO;
        BigDecimal p2 = powerPriceP2PerKwDay != null ? powerPriceP2PerKwDay : BigDecimal.ZERO;
        return p1.add(p2);
    }

    /**
     * Checks the extracted parts against the printed total and reports what the gap between them
     * means. The tax rates are never assumed — the ratio is measured, so an invoice billed under a
     * temporary reduced rate reconciles just as well as one under the ordinary rates. A ratio
     * outside every band means some extracted number is wrong, which is the case worth catching:
     * a misread price is silently plausible on its own and only shows up against the total.
     */
    public TaxBasis reconcileWithTotal() {
        BigDecimal energy = energyCostForPeriod();
        if (totalAmount == null || totalAmount.signum() <= 0 || energy == null || energy.signum() <= 0) {
            return TaxBasis.INSUFFICIENT_DATA;
        }
        BigDecimal power = powerCostForPeriod();
        if (power == null) return reconcileAgainstEnergyAlone(energy);

        BigDecimal ratio = totalAmount.divide(energy.add(power), 5, RoundingMode.HALF_UP);
        if (inRange(ratio, PRE_TAX_MIN, PRE_TAX_MAX))  return TaxBasis.PRE_TAX;
        if (inRange(ratio, POST_TAX_MIN, PRE_TAX_MIN)) return TaxBasis.POST_TAX;
        return TaxBasis.INCOHERENT;
    }

    /**
     * Without the power term the denominator is short by whatever power costs, so the ratio runs
     * high and the upper band cannot mean anything — this can still tell taxed prices from
     * untaxed ones, but it only calls INCOHERENT when energy alone overruns the printed total.
     * Detecting a subtler misread needs the power term.
     */
    private TaxBasis reconcileAgainstEnergyAlone(BigDecimal energy) {
        BigDecimal ratio = totalAmount.divide(energy, 5, RoundingMode.HALF_UP);
        if (ratio.compareTo(ENERGY_ONLY_MIN) < 0)              return TaxBasis.INCOHERENT;
        if (ratio.compareTo(ENERGY_ONLY_PRE_TAX_MIN) >= 0)     return TaxBasis.PRE_TAX;
        return TaxBasis.POST_TAX;
    }

    /**
     * Power cost for the billing period solved from the printed total when the power term was not
     * extracted, so a missing line does not delete a term worth ~13 % of a domestic invoice.
     * Assumes the ordinary tax rates, and overstates by whatever fixed lines the extraction omits
     * (bono social, meter rental — around 1.50 € a month), so the caller must widen its band.
     * Returns null when energy is unknown, or when the residual is not a plausible power cost.
     */
    public BigDecimal derivePowerCostForPeriod() {
        BigDecimal energy = energyCostForPeriod();
        if (totalAmount == null || energy == null || billingDays() == 0) return null;
        BigDecimal residual = totalAmount
                .divide(ORDINARY_TAX_FACTOR, 4, RoundingMode.HALF_UP)
                .subtract(energy);
        // A power term never exceeds the energy it accompanies on a domestic 2.0TD supply; a
        // residual that large means the energy figure is wrong, not that power is expensive.
        return residual.signum() > 0 && residual.compareTo(energy) <= 0 ? residual : null;
    }

    private static boolean inRange(BigDecimal value, BigDecimal minInclusive, BigDecimal maxExclusive) {
        return value.compareTo(minInclusive) >= 0 && value.compareTo(maxExclusive) < 0;
    }
}
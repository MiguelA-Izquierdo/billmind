package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        BigDecimal contractedPowerKw
) implements InvoiceFields {

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
                uniform, null, null, null, contractedPowerKw);
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
}
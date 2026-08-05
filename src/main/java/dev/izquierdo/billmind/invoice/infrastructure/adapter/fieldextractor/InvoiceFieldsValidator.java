package dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor;

import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Validates domain invariants on LLM-extracted invoice fields.
 * Throws InvoiceFieldExtractionException for any violation so the
 * caller can treat parse failure and validation failure uniformly.
 */
@Component
public class InvoiceFieldsValidator {

    public void validate(InvoiceFields fields) {
        requireDate(fields.billingPeriodStart(), "billingPeriodStart");
        requireDate(fields.billingPeriodEnd(),   "billingPeriodEnd");
        require(!fields.billingPeriodStart().isAfter(fields.billingPeriodEnd()),
                "billingPeriodStart must not be after billingPeriodEnd");
        requireAmount(fields.totalAmount(), "totalAmount");

        switch (fields) {
            case ElectricityFields e -> {
                requireNonNegative(e.consumptionKwh(),    "consumptionKwh");
                requireNonNegative(e.consumptionKwhP1(),  "consumptionKwhP1");
                requireNonNegative(e.consumptionKwhP2(),  "consumptionKwhP2");
                requireNonNegative(e.consumptionKwhP3(),  "consumptionKwhP3");
                requireNonNegative(e.contractedPowerKw(), "contractedPowerKw");
                boolean hasFlat = e.pricePerKwh() != null;
                boolean hasTou  = e.pricePerKwhP1() != null
                               || e.pricePerKwhP2() != null
                               || e.pricePerKwhP3() != null;
                require(!hasFlat || !hasTou,
                        "pricePerKwh cannot coexist with TOU prices (P1/P2/P3)");
                // The comparison engine needs a flat price, or P1+P3 to weight into one. Anything
                // less is a mute invoice: it would be stored only to fail silently at comparison.
                require(hasFlat || (e.pricePerKwhP1() != null && e.pricePerKwhP3() != null),
                        "no usable price: needs pricePerKwh, or both pricePerKwhP1 and pricePerKwhP3");
            }
            case GasFields g -> {
                requireNonNegative(g.consumptionM3(),  "consumptionM3");
                requireNonNegative(g.consumptionKwh(), "consumptionKwh");
            }
            case WaterFields w  -> requireNonNegative(w.consumptionM3(), "consumptionM3");
            case TelecomFields t -> requireNonNegative(t.monthlyFee(),   "monthlyFee");
        }
    }

    private static void requireDate(LocalDate date, String field) {
        if (date == null) fail(field + " is required");
    }

    private static void requireAmount(BigDecimal value, String field) {
        if (value == null) fail(field + " is required");
        else if (value.compareTo(BigDecimal.ZERO) < 0) fail(field + " must not be negative");
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) fail(field + " must not be negative");
    }

    private static void require(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String reason) {
        throw new InvoiceFieldExtractionException(new IllegalStateException(reason));
    }
}

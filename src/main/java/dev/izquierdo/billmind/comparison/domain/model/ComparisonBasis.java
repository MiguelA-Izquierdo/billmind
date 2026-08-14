package dev.izquierdo.billmind.comparison.domain.model;

/**
 * What the savings figure was actually built from. A saving is an estimate over a year the user
 * has not lived yet, so every consumer of it — the API, the chat UI, the assistant's context —
 * has to be able to say which parts were read off the invoice and which were assumed. Without
 * this, a number derived from a 32-day summer bill and a standard consumption profile reads
 * exactly like a measurement.
 */
public record ComparisonBasis(
        long observedDays,
        boolean annualised,
        PowerTerm powerTerm,
        ConsumptionProfile consumptionProfile,
        boolean taxesIncluded
) {

    /** Where the power term of the comparison came from. */
    public enum PowerTerm {
        /** Read off the invoice. */
        READ,
        /** Solved from the printed total because the invoice line was not extracted. */
        DERIVED,
        /** Neither available — both sides are compared on the energy term alone. */
        UNAVAILABLE
    }

    /** Whether the per-period split of consumption is the invoice's own or a standard profile. */
    public enum ConsumptionProfile { ACTUAL, ASSUMED }
}
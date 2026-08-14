package dev.izquierdo.billmind._shared.domain.model.fields;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ElectricityFieldsTest {

    // ── withFlatRateIfUniform ─────────────────────────────────────────────────

    /**
     * The Octopus invoice priced every period at 0.122 and the model stored it as TOU, sending the
     * comparison engine down the time-of-use branch for a flat-rate tariff.
     */
    @Test
    void shouldCollapseThreeEqualPeriodPricesIntoAFlatRate() {
        ElectricityFields fields = withPrices(null, "0.122", "0.122", "0.122");

        ElectricityFields result = fields.withFlatRateIfUniform();

        assertThat(result.pricePerKwh()).isEqualByComparingTo("0.122");
        assertThat(result.pricePerKwhP1()).isNull();
        assertThat(result.pricePerKwhP2()).isNull();
        assertThat(result.pricePerKwhP3()).isNull();
    }

    /** Two-period día/noche tariffs leave P2 empty; equal día and noche prices are still one price. */
    @Test
    void shouldCollapseATwoPeriodTariffPricedTheSameInBothPeriods() {
        ElectricityFields result = withPrices(null, "0.19", null, "0.19").withFlatRateIfUniform();

        assertThat(result.pricePerKwh()).isEqualByComparingTo("0.19");
        assertThat(result.pricePerKwhP1()).isNull();
        assertThat(result.pricePerKwhP3()).isNull();
    }

    /** Scale is presentation: 0.122 and 0.1220 are the same price, so BigDecimal.equals will not do. */
    @Test
    void shouldTreatPricesWrittenToDifferentScalesAsEqual() {
        ElectricityFields result = withPrices(null, "0.122", "0.1220", "0.12200").withFlatRateIfUniform();

        assertThat(result.pricePerKwh()).isEqualByComparingTo("0.122");
        assertThat(result.pricePerKwhP2()).isNull();
    }

    @Test
    void shouldLeaveAGenuineTimeOfUseTariffAlone() {
        ElectricityFields fields = withPrices(null, "0.15234", "0.10123", "0.06891");

        assertThat(fields.withFlatRateIfUniform()).isSameAs(fields);
    }

    /**
     * One period priced and the rest empty is a half-read TOU invoice. Collapsing it would turn a
     * failed extraction into a confident flat price; the validator rejecting it is the better outcome.
     */
    @Test
    void shouldNotCollapseWhenOnlyOnePeriodCarriesAPrice() {
        ElectricityFields fields = withPrices(null, "0.122", null, null);

        assertThat(fields.withFlatRateIfUniform()).isSameAs(fields);
    }

    @Test
    void shouldLeaveAnInvoiceThatAlreadyHasAFlatPriceAlone() {
        ElectricityFields fields = withPrices("0.21", null, null, null);

        assertThat(fields.withFlatRateIfUniform()).isSameAs(fields);
    }

    @Test
    void shouldLeaveAnInvoiceWithoutAnyPriceAlone() {
        ElectricityFields fields = withPrices(null, null, null, null);

        assertThat(fields.withFlatRateIfUniform()).isSameAs(fields);
    }

    @Test
    void shouldKeepEveryOtherFieldWhenCollapsing() {
        ElectricityFields result = withPrices(null, "0.122", "0.122", "0.122").withFlatRateIfUniform();

        assertThat(result.billingPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(result.billingPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(result.totalAmount()).isEqualByComparingTo("56.83");
        assertThat(result.consumptionKwh()).isEqualByComparingTo("208.0");
        assertThat(result.consumptionKwhP1()).isEqualByComparingTo("64.0");
        assertThat(result.consumptionKwhP2()).isEqualByComparingTo("69.0");
        assertThat(result.consumptionKwhP3()).isEqualByComparingTo("75.0");
        assertThat(result.contractedPowerKw()).isEqualByComparingTo("4.6");
    }

    // ── reconcileWithTotal ────────────────────────────────────────────────────

    /**
     * The arithmetic of a real Endesa 2.0TD bill: 32 days, 419,475 kWh at 0,217764 €/kWh and
     * 3,45 kW at 0,102630 + 0,022452 €/kW/día add up to 105,16 €, which the printed 135,64 €
     * exceeds by exactly what IEE and IVA take. Nothing here assumes a tax rate — the ratio is
     * measured, so a bill issued under a temporary reduced rate reconciles just the same.
     */
    @Test
    void shouldReadRealInvoiceAsPreTaxWhenPartsAndTotalDifferByTax() {
        assertThat(realInvoice("0.217764", "0.102630", "0.022452").reconcileWithTotal())
                .isEqualTo(ElectricityFields.TaxBasis.PRE_TAX);
    }

    /**
     * The failure the reconciliation exists for. 0,118 instead of 0,218 is one wrong digit and is
     * perfectly plausible in isolation — it only becomes visible against the printed total.
     */
    @Test
    void shouldRejectAMisreadEnergyPriceThatCannotAddUpToTheTotal() {
        assertThat(realInvoice("0.118000", "0.102630", "0.022452").reconcileWithTotal())
                .isEqualTo(ElectricityFields.TaxBasis.INCOHERENT);
    }

    @Test
    void shouldDetectPricesThatAlreadyCarryTax() {
        assertThat(realInvoice("0.277000", "0.130000", "0.029000").reconcileWithTotal())
                .isEqualTo(ElectricityFields.TaxBasis.POST_TAX);
    }

    /**
     * Without the power term the denominator is short, so the verdict rests on energy alone. It
     * can still separate taxed prices from untaxed ones; it cannot catch a subtler misread.
     */
    @Test
    void shouldStillClassifyTaxBasisWhenThePowerTermIsMissing() {
        assertThat(realInvoice("0.217764", null, null).reconcileWithTotal())
                .isEqualTo(ElectricityFields.TaxBasis.PRE_TAX);
    }

    @Test
    void shouldRejectEnergyThatAloneOverrunsTheInvoiceTotal() {
        assertThat(realInvoice("0.400000", null, null).reconcileWithTotal())
                .isEqualTo(ElectricityFields.TaxBasis.INCOHERENT);
    }

    @Test
    void shouldReportInsufficientDataWhenNoTotalWasExtracted() {
        ElectricityFields noTotal = new ElectricityFields(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 12),
                null, new BigDecimal("419.475"), null, null, null,
                new BigDecimal("0.217764"), null, null, null, new BigDecimal("3.45"), null, null);

        assertThat(noTotal.reconcileWithTotal()).isEqualTo(ElectricityFields.TaxBasis.INSUFFICIENT_DATA);
    }

    /** Solving the missing power term from the total recovers it to within the omitted fixed lines. */
    @Test
    void shouldDerivePowerCostCloseToTheInvoicesOwnPowerLine() {
        BigDecimal derived = realInvoice("0.217764", null, null).derivePowerCostForPeriod();

        // The bill charges 13,81 € of power. The residual also absorbs the bono social and meter
        // rental (1,53 €) that nothing extracts, so it lands high by about that much.
        assertThat(derived).isBetween(new BigDecimal("13.00"), new BigDecimal("16.00"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** A real Endesa 2.0TD invoice: 10/06/2026–12/07/2026, 419,475 kWh, 3,45 kW, 135,64 € total. */
    private static ElectricityFields realInvoice(String pricePerKwh, String powerP1, String powerP2) {
        return new ElectricityFields(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 12),
                new BigDecimal("135.64"), new BigDecimal("419.475"),
                null, null, null,
                new BigDecimal(pricePerKwh), null, null, null, new BigDecimal("3.45"),
                decimal(powerP1), decimal(powerP2));
    }


    private static ElectricityFields withPrices(String flat, String p1, String p2, String p3) {
        return new ElectricityFields(
                LocalDate.of(2026, 6, 21), LocalDate.of(2026, 7, 22),
                new BigDecimal("56.83"), new BigDecimal("208.0"),
                new BigDecimal("64.0"), new BigDecimal("69.0"), new BigDecimal("75.0"),
                decimal(flat), decimal(p1), decimal(p2), decimal(p3),
                new BigDecimal("4.6"), null, null);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
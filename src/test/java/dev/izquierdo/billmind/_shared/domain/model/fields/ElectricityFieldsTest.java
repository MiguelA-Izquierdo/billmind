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

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ElectricityFields withPrices(String flat, String p1, String p2, String p3) {
        return new ElectricityFields(
                LocalDate.of(2026, 6, 21), LocalDate.of(2026, 7, 22),
                new BigDecimal("56.83"), new BigDecimal("208.0"),
                new BigDecimal("64.0"), new BigDecimal("69.0"), new BigDecimal("75.0"),
                decimal(flat), decimal(p1), decimal(p2), decimal(p3),
                new BigDecimal("4.6"));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
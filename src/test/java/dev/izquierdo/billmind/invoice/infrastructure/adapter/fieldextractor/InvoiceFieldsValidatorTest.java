package dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor;

import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceFieldsValidatorTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 1, 31);

    private final InvoiceFieldsValidator validator = new InvoiceFieldsValidator();

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void shouldPassValidElectricityFields() {
        assertThatCode(() -> validator.validate(electricity().build())).doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidGasFields() {
        assertThatCode(() -> validator.validate(validGas())).doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidWaterFields() {
        assertThatCode(() -> validator.validate(validWater())).doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidTelecomFields() {
        assertThatCode(() -> validator.validate(validTelecom())).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowNullOptionalTypeSpecificFields() {
        ElectricityFields fields = electricity().consumptionKwh(null).power(null).build();
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── billingPeriod ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectNullBillingPeriodStart() {
        ElectricityFields fields = electricity().period(null, END).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNullBillingPeriodEnd() {
        ElectricityFields fields = electricity().period(START, null).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectStartAfterEnd() {
        ElectricityFields fields = electricity().period(END, START).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldAcceptSameDayPeriod() {
        ElectricityFields fields = electricity().period(START, START).build();
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── totalAmount ───────────────────────────────────────────────────────────

    @Test
    void shouldRejectNullTotalAmount() {
        ElectricityFields fields = electricity().total(null).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNegativeTotalAmount() {
        ElectricityFields fields = electricity().total("-1.00").build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldAcceptZeroTotalAmount() {
        ElectricityFields fields = electricity().total("0").build();
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── ElectricityFields ─────────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeConsumptionKwh() {
        ElectricityFields fields = electricity().consumptionKwh("-1").build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNegativeContractedPowerKw() {
        ElectricityFields fields = electricity().power("-3.3").build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectFlatAndTouPricesCoexisting() {
        ElectricityFields fields = electricity().price("0.15").touPrices("0.22", null, null).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldPassValidTouElectricityFields() {
        ElectricityFields fields = electricity().touPrices("0.22", "0.18", "0.14").build();
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── Usable price (the comparison engine needs one) ────────────────────────

    @Test
    void shouldRejectElectricityWithNoPriceAtAll() {
        ElectricityFields fields = electricity().noPrices().build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectTouPricesMissingP3() {
        ElectricityFields fields = electricity().touPrices("0.22", "0.18", null).build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectTouPricesMissingP1() {
        ElectricityFields fields = electricity().touPrices(null, "0.18", "0.14").build();
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldAcceptTwoPeriodTouWithoutP2() {
        ElectricityFields fields = electricity().touPrices("0.22", null, "0.14").build();
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── GasFields ─────────────────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeGasConsumptionM3() {
        GasFields fields = new GasFields(START, END, new BigDecimal("89.00"), new BigDecimal("-87"), null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNegativeGasConsumptionKwh() {
        GasFields fields = new GasFields(START, END, new BigDecimal("89.00"), null, new BigDecimal("-984"), null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    // ── WaterFields ───────────────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeWaterConsumptionM3() {
        WaterFields fields = new WaterFields(START, END, new BigDecimal("39.00"), new BigDecimal("-18"), null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    // ── TelecomFields ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeTelecomMonthlyFee() {
        TelecomFields fields = new TelecomFields(START, END, new BigDecimal("45.90"), null, null, null, null, List.of(), List.of(), new BigDecimal("-45.90"));
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static ElectricityBuilder electricity() {
        return new ElectricityBuilder();
    }

    /**
     * Twelve components, of which any test cares about one or two. The builder starts from a
     * valid flat-rate invoice so each test names only what it is actually exercising.
     */
    private static final class ElectricityBuilder {

        private LocalDate  start          = START;
        private LocalDate  end            = END;
        private BigDecimal total          = new BigDecimal("67.20");
        private BigDecimal consumptionKwh = new BigDecimal("320");
        private BigDecimal price          = new BigDecimal("0.1823");
        private BigDecimal priceP1;
        private BigDecimal priceP2;
        private BigDecimal priceP3;
        private BigDecimal power          = new BigDecimal("3.3");

        ElectricityBuilder period(LocalDate start, LocalDate end) {
            this.start = start;
            this.end   = end;
            return this;
        }

        ElectricityBuilder total(String value) {
            this.total = decimal(value);
            return this;
        }

        ElectricityBuilder consumptionKwh(String value) {
            this.consumptionKwh = decimal(value);
            return this;
        }

        ElectricityBuilder power(String value) {
            this.power = decimal(value);
            return this;
        }

        ElectricityBuilder price(String value) {
            this.price = decimal(value);
            return this;
        }

        /** Sets the TOU prices and drops the flat one, the way the extractor is told to. */
        ElectricityBuilder touPrices(String p1, String p2, String p3) {
            this.price   = null;
            this.priceP1 = decimal(p1);
            this.priceP2 = decimal(p2);
            this.priceP3 = decimal(p3);
            return this;
        }

        ElectricityBuilder noPrices() {
            return touPrices(null, null, null);
        }

        ElectricityFields build() {
            // Per-period consumption stays null: no test asserts on it beyond the negative case,
            // which the total already covers.
            return new ElectricityFields(start, end, total, consumptionKwh,
                    null, null, null, price, priceP1, priceP2, priceP3, power);
        }

        private static BigDecimal decimal(String value) {
            return value == null ? null : new BigDecimal(value);
        }
    }

    private static GasFields validGas() {
        return new GasFields(START, END,
                new BigDecimal("89.34"), new BigDecimal("87"), new BigDecimal("984"), new BigDecimal("0.0712"));
    }

    private static WaterFields validWater() {
        return new WaterFields(START, END,
                new BigDecimal("39.21"), new BigDecimal("18"), new BigDecimal("0.8234"), new BigDecimal("12.40"));
    }

    private static TelecomFields validTelecom() {
        return new TelecomFields(START, END,
                new BigDecimal("45.90"), 600, 20, 0, 0, List.of(), List.of(), new BigDecimal("45.90"));
    }
}
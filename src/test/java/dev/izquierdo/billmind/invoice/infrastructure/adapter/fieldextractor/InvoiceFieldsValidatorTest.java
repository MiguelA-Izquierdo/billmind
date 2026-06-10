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
        assertThatCode(() -> validator.validate(validElectricity())).doesNotThrowAnyException();
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
        ElectricityFields fields = new ElectricityFields(START, END, new BigDecimal("45.00"), null, null, null, null, null, null);
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── billingPeriod ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectNullBillingPeriodStart() {
        ElectricityFields fields = new ElectricityFields(null, END, new BigDecimal("45.00"), null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNullBillingPeriodEnd() {
        ElectricityFields fields = new ElectricityFields(START, null, new BigDecimal("45.00"), null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectStartAfterEnd() {
        ElectricityFields fields = new ElectricityFields(
                END, START, new BigDecimal("45.00"), null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldAcceptSameDayPeriod() {
        ElectricityFields fields = new ElectricityFields(START, START, new BigDecimal("5.00"), null, null, null, null, null, null);
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── totalAmount ───────────────────────────────────────────────────────────

    @Test
    void shouldRejectNullTotalAmount() {
        ElectricityFields fields = new ElectricityFields(START, END, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNegativeTotalAmount() {
        ElectricityFields fields = new ElectricityFields(START, END, new BigDecimal("-1.00"), null, null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldAcceptZeroTotalAmount() {
        ElectricityFields fields = new ElectricityFields(START, END, BigDecimal.ZERO, null, null, null, null, null, null);
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    // ── ElectricityFields ─────────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeConsumptionKwh() {
        ElectricityFields fields = new ElectricityFields(
                START, END, new BigDecimal("45.00"), new BigDecimal("-1"), null, null, null, null, null);
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectNegativeContractedPowerKw() {
        ElectricityFields fields = new ElectricityFields(
                START, END, new BigDecimal("45.00"), null, null, null, null, null, new BigDecimal("-3.3"));
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldRejectFlatAndTouPricesCoexisting() {
        ElectricityFields fields = new ElectricityFields(
                START, END, new BigDecimal("45.00"), new BigDecimal("300"),
                new BigDecimal("0.15"), new BigDecimal("0.22"), null, null, new BigDecimal("3.3"));
        assertThatThrownBy(() -> validator.validate(fields))
                .isInstanceOf(InvoiceFieldExtractionException.class);
    }

    @Test
    void shouldPassValidTouElectricityFields() {
        ElectricityFields fields = new ElectricityFields(
                START, END, new BigDecimal("67.20"), new BigDecimal("320"),
                null, new BigDecimal("0.22"), new BigDecimal("0.18"), new BigDecimal("0.14"), new BigDecimal("3.3"));
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

    // ── Factories ─────────────────────────────────────────────────────────────

    private static ElectricityFields validElectricity() {
        return new ElectricityFields(START, END,
                new BigDecimal("67.20"), new BigDecimal("320"), new BigDecimal("0.1823"),
                null, null, null, new BigDecimal("3.3"));
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
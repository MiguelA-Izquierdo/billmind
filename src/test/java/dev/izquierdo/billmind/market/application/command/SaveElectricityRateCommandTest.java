package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaveElectricityRateCommandTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 1, 1);
    private static final UUID ID = UUID.randomUUID();

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            null, InvoiceType.LUZ, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenSupplyTypeIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            ID, null, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenCompanyIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            ID, InvoiceType.LUZ, null, "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenTariffNameIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            ID, InvoiceType.LUZ, "IBERDROLA", null,
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, "REE", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenValidFromIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            ID, InvoiceType.LUZ, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            null, null, null, "REE", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenSourceIsNull() {
        assertThatThrownBy(() -> new SaveElectricityRateCommand(
            ID, InvoiceType.LUZ, "IBERDROLA", "2.0TD",
            new BigDecimal("0.15"), null, null, null, null, null,
            TODAY, null, null, null, null
        )).isInstanceOf(NullPointerException.class);
    }
}
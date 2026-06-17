package dev.izquierdo.billmind.market.domain.model;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.market.domain.exceptions.InvalidElectricityRateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElectricityRateTest {

    private static final UUID ID          = UUID.randomUUID();
    private static final LocalDate TODAY  = LocalDate.of(2025, 1, 1);

    @Test
    void shouldBuildElectricityRateWithAllRequiredFields() {
        ElectricityRate rate = ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.150000"))
            .validFrom(TODAY)
            .source("REE")
            .build();

        assertThat(rate.getId()).isEqualTo(ID);
        assertThat(rate.getSupplyType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(rate.getCompany()).isEqualTo("IBERDROLA");
        assertThat(rate.getTariffName()).isEqualTo("2.0TD");
        assertThat(rate.getPricePerKwh()).isEqualByComparingTo("0.150000");
        assertThat(rate.getValidFrom()).isEqualTo(TODAY);
        assertThat(rate.getSource()).isEqualTo("REE");
        assertThat(rate.getReceivedAt()).isNotNull();
    }

    @Test
    void shouldAllowNullOptionalFields() {
        ElectricityRate rate = ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.GAS)
            .company("NATURGY")
            .tariffName("GAS-BASE")
            .pricePerKwh(new BigDecimal("0.080000"))
            .validFrom(TODAY)
            .source("CNMC")
            .build();

        assertThat(rate.getContractedPowerPrice()).isNull();
        assertThat(rate.getValidTo()).isNull();
        assertThat(rate.getRegion()).isNull();
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> ElectricityRate.builder(null)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenSupplyTypeIsNull() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenNoPriceIsProvided() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class)
         .hasMessageContaining("pricePerKwh");
    }

    @Test
    void shouldThrowWhenCompanyIsNull() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenCompanyIsBlank() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("   ")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class);
    }

    @Test
    void shouldThrowWhenTariffNameIsBlank() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("   ")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class);
    }

    @Test
    void shouldThrowWhenSourceIsNull() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenSourceIsBlank() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .source("")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class);
    }

    @Test
    void shouldThrowWhenPricePerKwhIsNegative() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("-0.01"))
            .validFrom(TODAY)
            .source("REE")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class);
    }

    @Test
    void shouldThrowWhenValidToIsBeforeValidFrom() {
        assertThatThrownBy(() -> ElectricityRate.builder(ID)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.15"))
            .validFrom(TODAY)
            .validTo(TODAY.minusDays(1))
            .source("REE")
            .build()
        ).isInstanceOf(InvalidElectricityRateException.class);
    }
}
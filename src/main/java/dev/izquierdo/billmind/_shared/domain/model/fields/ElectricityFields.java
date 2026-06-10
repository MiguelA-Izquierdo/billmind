package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ElectricityFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        BigDecimal consumptionKwh,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhP1,
        BigDecimal pricePerKwhP2,
        BigDecimal pricePerKwhP3,
        BigDecimal contractedPowerKw
) implements InvoiceFields {}
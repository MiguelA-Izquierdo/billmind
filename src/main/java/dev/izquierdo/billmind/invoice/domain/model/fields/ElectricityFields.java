package dev.izquierdo.billmind.invoice.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ElectricityFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        BigDecimal consumptionKwh,
        BigDecimal pricePerKwh,
        BigDecimal contractedPowerKw
) implements InvoiceFields {}
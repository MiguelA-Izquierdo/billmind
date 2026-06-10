package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GasFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        BigDecimal consumptionM3,
        BigDecimal consumptionKwh,
        BigDecimal pricePerKwh
) implements InvoiceFields {}
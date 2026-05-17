package dev.izquierdo.billmind.invoice.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WaterFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        BigDecimal consumptionM3,
        BigDecimal pricePerM3,
        BigDecimal sewageCharge
) implements InvoiceFields {}
package dev.izquierdo.billmind.invoice.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface InvoiceFields
        permits ElectricityFields, GasFields, WaterFields, TelecomFields {

    LocalDate billingPeriodStart();
    LocalDate billingPeriodEnd();
    BigDecimal totalAmount();
}
package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TelecomFields(
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount,
        Integer contractedSpeedMbps,
        Integer mobileDataGb,
        Integer includedMobileLines,
        Integer mobileLineCount,
        List<MobileLine> lines,
        List<StreamingService> streamingServices,
        BigDecimal monthlyFee
) implements InvoiceFields {}
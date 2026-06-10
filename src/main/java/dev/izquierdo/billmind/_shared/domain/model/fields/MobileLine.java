package dev.izquierdo.billmind._shared.domain.model.fields;

import java.math.BigDecimal;

public record MobileLine(
        String lineType,
        String planName,
        BigDecimal baseAmount,
        BigDecimal discount
) {}
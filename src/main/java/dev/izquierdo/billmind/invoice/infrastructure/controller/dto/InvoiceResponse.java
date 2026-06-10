package dev.izquierdo.billmind.invoice.infrastructure.controller.dto;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID sessionId,
        String fileName,
        String supplyType,
        String provider,
        Instant uploadedAt,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BigDecimal totalAmount
) {
    public static InvoiceResponse from(Invoice invoice) {
        InvoiceFields fields = invoice.getFields();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getSessionId(),
                invoice.getFileName(),
                invoice.getSupplyType() != null ? invoice.getSupplyType().name() : null,
                invoice.getProvider(),
                invoice.getUploadedAt(),
                fields != null ? fields.billingPeriodStart() : null,
                fields != null ? fields.billingPeriodEnd() : null,
                fields != null ? fields.totalAmount() : null
        );
    }
}
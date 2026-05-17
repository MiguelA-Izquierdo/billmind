package dev.izquierdo.billmind.invoice.infrastructure.controller.dto;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID sessionId,
        String fileName,
        String supplyType,
        String provider,
        Instant uploadedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getSessionId(),
                invoice.getFileName(),
                invoice.getSupplyType() != null ? invoice.getSupplyType().name() : null,
                invoice.getProvider(),
                invoice.getUploadedAt()
        );
    }
}
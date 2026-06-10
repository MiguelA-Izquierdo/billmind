package dev.izquierdo.billmind.invoice.infrastructure.controller.dto;

import dev.izquierdo.billmind.comparison.infrastructure.controller.dto.ComparisonResponseDTO;

import java.util.UUID;

public record InvoiceUploadResponse(
        UUID invoiceId,
        String fileName,
        ComparisonResponseDTO comparison
) {}


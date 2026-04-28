package com.demo.billmind.invoice.infrastructure.controller.dto;

import java.util.UUID;

public record InvoiceUploadResponse(UUID invoiceId, String fileName) {
}

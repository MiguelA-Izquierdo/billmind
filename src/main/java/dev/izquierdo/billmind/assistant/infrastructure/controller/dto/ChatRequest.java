package dev.izquierdo.billmind.assistant.infrastructure.controller.dto;

import java.util.UUID;

public record ChatRequest(UUID invoiceId, String message) {}
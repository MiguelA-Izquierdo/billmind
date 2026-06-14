package dev.izquierdo.billmind.knowledge.infrastructure.controller.dto;

import dev.izquierdo.billmind.knowledge.domain.model.DocType;

import java.time.LocalDate;
import java.util.UUID;

public record IngestRequest(
        UUID docId,
        DocType docType,
        String title,
        String source,
        String content,
        LocalDate validFrom,
        LocalDate validTo
) {}
package dev.izquierdo.billmind.knowledge.infrastructure.controller.dto;

import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.time.LocalDate;
import java.util.UUID;

public record IngestRequest(
        UUID docId,
        DocType docType,
        SupplyDomain supplyDomain,
        String title,
        String source,
        String content,
        LocalDate validFrom,
        LocalDate validTo
) {}
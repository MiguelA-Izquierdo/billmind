package dev.izquierdo.billmind.knowledge.application.command;

import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;
import dev.izquierdo.billmind._shared.application.command.Command;

import java.time.LocalDate;

public record IngestDocumentCommand(
        DocType docType,
        String title,
        String source,
        String content,
        LocalDate validFrom,
        LocalDate validTo
) implements Command {

    public IngestDocumentCommand {
        if (docType == null)               throw new IllegalArgumentException("docType cannot be null");
        if (title == null || title.isBlank())   throw new IllegalArgumentException("title cannot be blank");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source cannot be blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content cannot be blank");
    }
}
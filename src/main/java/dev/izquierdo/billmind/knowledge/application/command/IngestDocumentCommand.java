package dev.izquierdo.billmind.knowledge.application.command;

import dev.izquierdo.billmind.knowledge.domain.model.DocType;
import dev.izquierdo.billmind._shared.application.command.Command;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;

import java.time.LocalDate;
import java.util.UUID;

public record IngestDocumentCommand(
        UUID docId,
        DocType docType,
        SupplyDomain supplyDomain,
        String title,
        String source,
        String content,
        LocalDate validFrom,
        LocalDate validTo
) implements Command {

    /** Upper bound on document body length — guards against oversized corpus rows and runaway embedding cost. */
    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final int MAX_TITLE_LENGTH   = 500;

    public IngestDocumentCommand {
        if (docId == null)                        throw new IllegalArgumentException("docId cannot be null");
        if (docType == null)                      throw new IllegalArgumentException("docType cannot be null");
        if (supplyDomain == null)                 throw new IllegalArgumentException("supplyDomain cannot be null");
        if (title == null || title.isBlank())     throw new IllegalArgumentException("title cannot be blank");
        if (title.length() > MAX_TITLE_LENGTH)    throw new IllegalArgumentException("title exceeds " + MAX_TITLE_LENGTH + " characters");
        if (source == null || source.isBlank())   throw new IllegalArgumentException("source cannot be blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content cannot be blank");
        if (content.length() > MAX_CONTENT_LENGTH) throw new IllegalArgumentException("content exceeds " + MAX_CONTENT_LENGTH + " characters");
        if (validTo != null && validFrom != null && validTo.isBefore(validFrom))
            throw new IllegalArgumentException("validTo cannot be before validFrom");
    }
}
package dev.izquierdo.billmind.knowledge.application.usecase;

import dev.izquierdo.billmind.knowledge.application.command.IngestDocumentCommand;
import dev.izquierdo.billmind.knowledge.domain.exceptions.EmptyDocumentException;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeChunk;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeDocument;
import dev.izquierdo.billmind.knowledge.domain.port.DocumentChunker;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class IngestDocumentUseCase {

    private final KnowledgeRepository repository;
    private final DocumentChunker chunker;

    public IngestDocumentUseCase(KnowledgeRepository repository, DocumentChunker chunker) {
        this.repository = Objects.requireNonNull(repository, "KnowledgeRepository cannot be null");
        this.chunker    = Objects.requireNonNull(chunker,    "DocumentChunker cannot be null");
    }

    public void execute(IngestDocumentCommand command) {
        UUID docId = command.docId();

        KnowledgeDocument document = KnowledgeDocument.create(
                docId, command.docType(), command.supplyDomain(), command.title(), command.source(),
                command.validFrom(), command.validTo());

        List<String> rawChunks = chunker.chunk(command.content());

        if (rawChunks.isEmpty()) {
            throw new EmptyDocumentException(command.source());
        }

        List<KnowledgeChunk> chunks = IntStream.range(0, rawChunks.size())
                .mapToObj(i -> KnowledgeChunk.create(UUID.randomUUID(), docId, rawChunks.get(i), i))
                .toList();

        repository.upsert(document, chunks);

    }
}
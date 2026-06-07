package dev.izquierdo.billmind.knowledge.application.command;

import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import dev.izquierdo.billmind.knowledge.application.usecase.IngestDocumentUseCase;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class IngestDocumentCommandHandler implements CommandHandler<IngestDocumentCommand> {

    private final IngestDocumentUseCase useCase;

    public IngestDocumentCommandHandler(IngestDocumentUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "IngestDocumentUseCase cannot be null");
    }

    @Override
    public void handle(IngestDocumentCommand command) {
        useCase.execute(command);
    }

    @Override
    public Class<IngestDocumentCommand> commandType() {
        return IngestDocumentCommand.class;
    }
}
package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import dev.izquierdo.billmind.market.application.usecase.DeleteAllElectricityRatesUseCase;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DeleteAllElectricityRatesCommandHandler implements CommandHandler<DeleteAllElectricityRatesCommand> {

    private final DeleteAllElectricityRatesUseCase useCase;

    public DeleteAllElectricityRatesCommandHandler(DeleteAllElectricityRatesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "DeleteAllElectricityRatesUseCase cannot be null");
    }

    @Override
    public Class<DeleteAllElectricityRatesCommand> commandType() {
        return DeleteAllElectricityRatesCommand.class;
    }

    @Override
    public void handle(DeleteAllElectricityRatesCommand command) {
        useCase.execute();
    }
}
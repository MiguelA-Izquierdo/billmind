package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import dev.izquierdo.billmind.market.application.usecase.SaveElectricityRateUseCase;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SaveElectricityRateCommandHandler implements CommandHandler<SaveElectricityRateCommand> {

    private final SaveElectricityRateUseCase saveElectricityRateUseCase;

    public SaveElectricityRateCommandHandler(SaveElectricityRateUseCase saveElectricityRateUseCase) {
        this.saveElectricityRateUseCase = Objects.requireNonNull(saveElectricityRateUseCase);
    }

    @Override
    public Class<SaveElectricityRateCommand> commandType() {
        return SaveElectricityRateCommand.class;
    }

    @Override
    public void handle(SaveElectricityRateCommand command) {
        ElectricityRate rate = ElectricityRate.builder(command.id())
            .supplyType(command.supplyType())
            .company(command.company())
            .tariffName(command.tariffName())
            .pricePerKwh(command.pricePerKwh())
            .pricePerKwhValle(command.pricePerKwhValle())
            .pricePerKwhLlano(command.pricePerKwhLlano())
            .pricePerKwhPunta(command.pricePerKwhPunta())
            .contractedPowerPrice(command.contractedPowerPrice())
            .contractedPowerPriceP2(command.contractedPowerPriceP2())
            .validFrom(command.validFrom())
            .validTo(command.validTo())
            .region(command.region())
            .source(command.source())
            .receivedAt(command.receivedAt())
            .build();
        saveElectricityRateUseCase.execute(rate);
    }
}
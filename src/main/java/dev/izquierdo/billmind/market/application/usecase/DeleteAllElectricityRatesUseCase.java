package dev.izquierdo.billmind.market.application.usecase;

import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DeleteAllElectricityRatesUseCase {

    private final ElectricityRateRepository repository;

    public DeleteAllElectricityRatesUseCase(ElectricityRateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "ElectricityRateRepository cannot be null");
    }

    public void execute() {
        repository.deleteAll();
    }
}
package dev.izquierdo.billmind.market.application.usecase;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class GetElectricityRatesUseCase {

    private final ElectricityRateRepository repository;

    public GetElectricityRatesUseCase(ElectricityRateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "ElectricityRateRepository cannot be null");
    }

    public List<ElectricityRate> execute() {
        return repository.findAll();
    }
}
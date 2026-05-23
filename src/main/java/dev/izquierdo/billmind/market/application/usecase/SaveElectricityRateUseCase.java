package dev.izquierdo.billmind.market.application.usecase;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class SaveElectricityRateUseCase {

    private final ElectricityRateRepository repository;

    public SaveElectricityRateUseCase(ElectricityRateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "ElectricityRateRepository cannot be null");
    }

    public void execute(ElectricityRate rate) {
//        log.info("Vamos a guardar el rate: {}", rate.toString());
        repository.save(rate);
    }
}
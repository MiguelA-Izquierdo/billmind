package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.port.MarketRatesContextPort;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarketRatesContextAdapter implements MarketRatesContextPort {

    private final ElectricityRateRepository electricityRateRepository;

    public MarketRatesContextAdapter(ElectricityRateRepository electricityRateRepository) {
        this.electricityRateRepository = electricityRateRepository;
    }

    @Override
    public List<MarketRateSnapshot> loadLatestRates(SupplyDomain domain) {
        if (domain != SupplyDomain.ELECTRICITY) return List.of();
        return electricityRateRepository.findLatestPerTariff().stream()
                .map(r -> new MarketRateSnapshot(
                        r.getCompany(),
                        r.getTariffName(),
                        r.getPricePerKwh(),
                        r.getPricePerKwhValle(),
                        r.getPricePerKwhLlano(),
                        r.getPricePerKwhPunta(),
                        r.getContractedPowerPrice(),
                        r.getContractedPowerPriceP2(),
                        r.getValidFrom()))
                .toList();
    }
}
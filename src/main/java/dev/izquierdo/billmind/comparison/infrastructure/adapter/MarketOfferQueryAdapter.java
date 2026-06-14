package dev.izquierdo.billmind.comparison.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.MarketOffer;
import dev.izquierdo.billmind.comparison.domain.port.MarketOfferQueryPort;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class MarketOfferQueryAdapter implements MarketOfferQueryPort {

    private final ElectricityRateRepository electricityRateRepository;

    public MarketOfferQueryAdapter(ElectricityRateRepository electricityRateRepository) {
        this.electricityRateRepository = Objects.requireNonNull(electricityRateRepository);
    }

    @Override
    public List<MarketOffer> findBySupplyType(InvoiceType supplyType) {
        if (supplyType != InvoiceType.LUZ) return List.of();
        return electricityRateRepository.findLatestPerTariff().stream()
                .<MarketOffer>map(this::toMarketOffer)
                .toList();
    }

    private ElectricityMarketOffer toMarketOffer(ElectricityRate rate) {
        return new ElectricityMarketOffer(
                rate.getCompany(),
                rate.getTariffName(),
                rate.getPricePerKwh(),
                rate.getPricePerKwhValle(),
                rate.getPricePerKwhLlano(),
                rate.getPricePerKwhPunta(),
                rate.getContractedPowerPrice()
        );
    }
}
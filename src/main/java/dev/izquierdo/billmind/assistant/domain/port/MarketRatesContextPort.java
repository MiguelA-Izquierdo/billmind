package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;

import java.util.List;

public interface MarketRatesContextPort {
    List<MarketRateSnapshot> loadLatestRates(SupplyDomain domain);
}
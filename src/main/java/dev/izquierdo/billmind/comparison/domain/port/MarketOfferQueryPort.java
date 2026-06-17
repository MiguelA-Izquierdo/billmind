package dev.izquierdo.billmind.comparison.domain.port;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.comparison.domain.model.MarketOffer;

import java.util.List;

public interface MarketOfferQueryPort {
    List<MarketOffer> findBySupplyType(SupplyDomain supplyType);
}
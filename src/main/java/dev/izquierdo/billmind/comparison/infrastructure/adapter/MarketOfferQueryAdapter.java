package dev.izquierdo.billmind.comparison.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.MarketOffer;
import dev.izquierdo.billmind.comparison.domain.port.MarketOfferQueryPort;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class MarketOfferQueryAdapter implements MarketOfferQueryPort {

    private static final Logger log = LoggerFactory.getLogger(MarketOfferQueryAdapter.class);
    private static final String FALLBACK_RESOURCE = "comparison/fallback-electricity-offers.json";

    private final ElectricityRateRepository electricityRateRepository;

    /**
     * Example offers, read once at startup and served only while the rate corpus is empty, so a
     * fresh install can exercise the comparison without a Kafka producer. Never persisted: they
     * need no cleanup, and {@code GET /api/v1/admin/market-rates} keeps reporting the real corpus.
     */
    private final List<MarketOffer> fallbackOffers;

    public MarketOfferQueryAdapter(
            ElectricityRateRepository electricityRateRepository,
            ObjectMapper objectMapper,
            @Value("${comparison.fallback-offers.enabled:true}") boolean fallbackEnabled) {
        this.electricityRateRepository = Objects.requireNonNull(electricityRateRepository);
        this.fallbackOffers = fallbackEnabled ? loadFallbackOffers(objectMapper) : List.of();
    }

    @Override
    public List<MarketOffer> findBySupplyType(SupplyDomain supplyType) {
        if (supplyType != SupplyDomain.ELECTRICITY) return List.of();
        List<MarketOffer> offers = electricityRateRepository.findLatestPerTariff().stream()
                .<MarketOffer>map(this::toMarketOffer)
                .toList();
        if (!offers.isEmpty()) return offers;
        // findAll() keeps expired rates, so a non-empty corpus here means rates arrived and went
        // stale. The fallback covers a corpus that was never filled — quoting example prices for a
        // stale one would be inventing a market.
        if (!electricityRateRepository.findAll().isEmpty()) {
            log.warn("Market rates exist but none are currently valid — reporting no alternatives");
            return List.of();
        }
        log.debug("No market rates persisted — serving {} fallback example offers", fallbackOffers.size());
        return fallbackOffers;
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

    private static List<MarketOffer> loadFallbackOffers(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(FALLBACK_RESOURCE).getInputStream()) {
            FallbackFile file = objectMapper.readValue(in, FallbackFile.class);
            List<MarketOffer> loaded = file.offers().stream().<MarketOffer>map(FallbackOffer::toDomain).toList();
            log.info("Loaded {} fallback electricity offers from {}", loaded.size(), FALLBACK_RESOURCE);
            return loaded;
        } catch (Exception ex) {
            // A missing or malformed file must never break startup: the comparison then reports
            // no alternatives, exactly as it did before this fallback existed.
            log.warn("Could not load fallback electricity offers from {}: {}", FALLBACK_RESOURCE, ex.getMessage());
            return List.of();
        }
    }

    record FallbackFile(String note, List<FallbackOffer> offers) {}

    record FallbackOffer(
            String company,
            String tariffName,
            BigDecimal pricePerKwh,
            BigDecimal pricePerKwhValle,
            BigDecimal pricePerKwhLlano,
            BigDecimal pricePerKwhPunta,
            BigDecimal contractedPowerPrice
    ) {
        ElectricityMarketOffer toDomain() {
            return new ElectricityMarketOffer(company, tariffName, pricePerKwh,
                    pricePerKwhValle, pricePerKwhLlano, pricePerKwhPunta, contractedPowerPrice);
        }
    }
}
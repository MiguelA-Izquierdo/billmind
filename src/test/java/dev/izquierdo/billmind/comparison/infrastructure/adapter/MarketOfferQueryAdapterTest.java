package dev.izquierdo.billmind.comparison.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.MarketOffer;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOfferQueryAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ElectricityRateRepository repository;

    private MarketOfferQueryAdapter adapterWithFallback() {
        return new MarketOfferQueryAdapter(repository, MAPPER, true);
    }

    @Test
    void shouldReturnPersistedRatesWhenCorpusHasRates() {
        when(repository.findLatestPerTariff()).thenReturn(List.of(rate("Compañía Real", "Tarifa Real")));

        List<MarketOffer> offers = adapterWithFallback().findBySupplyType(SupplyDomain.ELECTRICITY);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0)).isInstanceOf(ElectricityMarketOffer.class);
        assertThat(((ElectricityMarketOffer) offers.get(0)).company()).isEqualTo("Compañía Real");
    }

    @Test
    void shouldServeFallbackOffersWhenCorpusIsEmpty() {
        when(repository.findLatestPerTariff()).thenReturn(List.of());

        List<MarketOffer> offers = adapterWithFallback().findBySupplyType(SupplyDomain.ELECTRICITY);

        assertThat(offers).hasSize(6);
        assertThat(offers).allSatisfy(o -> assertThat(((ElectricityMarketOffer) o).company()).isNotBlank());
    }

    /**
     * The comparison builds a flat block and a time-of-use block independently, each needing a
     * winner plus alternatives — so the fallback must populate both.
     */
    @Test
    void shouldSplitFallbackOffersBetweenFlatAndTimeOfUse() {
        when(repository.findLatestPerTariff()).thenReturn(List.of());

        List<ElectricityMarketOffer> offers = adapterWithFallback()
                .findBySupplyType(SupplyDomain.ELECTRICITY).stream()
                .map(ElectricityMarketOffer.class::cast)
                .toList();

        assertThat(offers).filteredOn(o -> !o.isTou()).hasSize(3);
        assertThat(offers).filteredOn(ElectricityMarketOffer::isTou).hasSize(3);
        assertThat(offers).allSatisfy(o -> assertThat(o.contractedPowerPrice()).isNotNull());
    }

    @Test
    void shouldReturnEmptyWhenCorpusIsEmptyAndFallbackDisabled() {
        when(repository.findLatestPerTariff()).thenReturn(List.of());

        List<MarketOffer> offers = new MarketOfferQueryAdapter(repository, MAPPER, false)
                .findBySupplyType(SupplyDomain.ELECTRICITY);

        assertThat(offers).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenSupplyTypeIsNotElectricity() {
        List<MarketOffer> offers = adapterWithFallback().findBySupplyType(SupplyDomain.GAS);

        assertThat(offers).isEmpty();
    }

    private static ElectricityRate rate(String company, String tariffName) {
        return ElectricityRate.builder(UUID.randomUUID())
                .supplyType(SupplyDomain.ELECTRICITY)
                .company(company)
                .tariffName(tariffName)
                .pricePerKwh(new BigDecimal("0.150000"))
                .contractedPowerPrice(new BigDecimal("0.110000"))
                .validFrom(LocalDate.of(2026, 1, 1))
                .source("test")
                .build();
    }
}
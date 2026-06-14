package dev.izquierdo.billmind.comparison.application.usecase;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.comparison.application.ElectricityComparisonCalculator;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.port.MarketOfferQueryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CompareInvoiceUseCase {

    private final MarketOfferQueryPort marketOfferQueryPort;
    private final ElectricityComparisonCalculator electricityCalculator;

    public CompareInvoiceUseCase(MarketOfferQueryPort marketOfferQueryPort,
                                  ElectricityComparisonCalculator electricityCalculator) {
        this.marketOfferQueryPort  = Objects.requireNonNull(marketOfferQueryPort);
        this.electricityCalculator = Objects.requireNonNull(electricityCalculator);
    }

    public Optional<ComparisonResult> compare(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields ef -> compareElectricity(ef);
            default                   -> Optional.empty();
        };
    }

    private Optional<ComparisonResult> compareElectricity(ElectricityFields fields) {
        List<ElectricityMarketOffer> offers = marketOfferQueryPort.findBySupplyType(InvoiceType.LUZ)
                .stream()
                .filter(o -> o instanceof ElectricityMarketOffer)
                .map(o -> (ElectricityMarketOffer) o)
                .toList();
        return electricityCalculator.calculate(fields, offers)
                .map(r -> (ComparisonResult) r);
    }
}
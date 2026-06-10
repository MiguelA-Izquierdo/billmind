package dev.izquierdo.billmind.comparison.application.usecase;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityAlternativeRate;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.port.MarketOfferQueryPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CompareInvoiceUseCase {

    private final MarketOfferQueryPort marketOfferQueryPort;

    public CompareInvoiceUseCase(MarketOfferQueryPort marketOfferQueryPort) {
        this.marketOfferQueryPort = Objects.requireNonNull(marketOfferQueryPort);
    }

    public Optional<ComparisonResult> compare(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields ef -> compareElectricity(ef);
            default                   -> Optional.empty();
        };
    }

    private Optional<ComparisonResult> compareElectricity(ElectricityFields fields) {
        BigDecimal userPrice = effectiveUserPrice(fields);
        if (userPrice == null || fields.consumptionKwh() == null) return Optional.empty();

        List<ElectricityMarketOffer> offers = marketOfferQueryPort.findBySupplyType(InvoiceType.LUZ)
                .stream()
                .filter(o -> o instanceof ElectricityMarketOffer)
                .map(o -> (ElectricityMarketOffer) o)
                .toList();

        if (offers.isEmpty()) return Optional.empty();

        long billingDays = ChronoUnit.DAYS.between(fields.billingPeriodStart(), fields.billingPeriodEnd());
        if (billingDays <= 0) return Optional.empty();

        BigDecimal annualKwh = fields.consumptionKwh()
                .multiply(BigDecimal.valueOf(365))
                .divide(BigDecimal.valueOf(billingDays), 4, RoundingMode.HALF_UP);

        record RankedOffer(ElectricityMarketOffer offer, BigDecimal effectivePrice) {}

        List<RankedOffer> ranked = offers.stream()
                .map(o -> new RankedOffer(o, effectivePrice(o)))
                .filter(r -> r.effectivePrice() != null)
                .sorted(Comparator.comparing(RankedOffer::effectivePrice))
                .toList();

        if (ranked.isEmpty()) return Optional.empty();

        RankedOffer best = ranked.get(0);
        List<ElectricityAlternativeRate> alternatives = ranked.stream()
                .skip(1).limit(3)
                .map(r -> new ElectricityAlternativeRate(
                        r.offer().company(), r.offer().tariffName(),
                        r.effectivePrice(), r.offer().isTou()))
                .toList();

        BigDecimal annualSavings = userPrice
                .subtract(best.effectivePrice())
                .multiply(annualKwh)
                .setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new ElectricityComparisonResult(
                userPrice,
                best.offer().company(),
                best.offer().tariffName(),
                best.effectivePrice(),
                best.offer().isTou(),
                annualKwh.setScale(2, RoundingMode.HALF_UP),
                annualSavings,
                alternatives,
                Instant.now()
        ));
    }

    private BigDecimal effectiveUserPrice(ElectricityFields fields) {
        if (fields.pricePerKwh() != null) return fields.pricePerKwh();
        if (fields.pricePerKwhP1() != null && fields.pricePerKwhP2() != null && fields.pricePerKwhP3() != null) {
            return fields.pricePerKwhP1().multiply(BigDecimal.valueOf(0.30))
                    .add(fields.pricePerKwhP2().multiply(BigDecimal.valueOf(0.40)))
                    .add(fields.pricePerKwhP3().multiply(BigDecimal.valueOf(0.30)))
                    .setScale(6, RoundingMode.HALF_UP);
        }
        if (fields.pricePerKwhP1() != null && fields.pricePerKwhP3() != null) {
            return fields.pricePerKwhP1().multiply(BigDecimal.valueOf(0.60))
                    .add(fields.pricePerKwhP3().multiply(BigDecimal.valueOf(0.40)))
                    .setScale(6, RoundingMode.HALF_UP);
        }
        return null;
    }

    private BigDecimal effectivePrice(ElectricityMarketOffer offer) {
        if (offer.pricePerKwh() != null) return offer.pricePerKwh();
        if (offer.pricePerKwhValle() != null
                && offer.pricePerKwhLlano() != null
                && offer.pricePerKwhPunta() != null) {
            return offer.pricePerKwhValle()
                    .add(offer.pricePerKwhLlano())
                    .add(offer.pricePerKwhPunta())
                    .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
        }
        return null;
    }
}
package dev.izquierdo.billmind.comparison.application;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityAlternativeRate;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ElectricityComparisonCalculator {

    public Optional<ElectricityComparisonResult> calculate(
            ElectricityFields fields, List<ElectricityMarketOffer> offers) {

        boolean userIsTou = fields.pricePerKwhP1() != null && periodPricesDiffer(fields);
        BigDecimal[] weights  = touWeights(fields);
        BigDecimal  userPrice = effectiveUserPrice(fields, weights);

        if (userPrice == null || fields.consumptionKwh() == null) return Optional.empty();
        if (offers.isEmpty()) return Optional.empty();

        long billingDays = ChronoUnit.DAYS.between(fields.billingPeriodStart(), fields.billingPeriodEnd());
        if (billingDays <= 0) return Optional.empty();

        BigDecimal annualKwh = fields.consumptionKwh()
                .multiply(BigDecimal.valueOf(365))
                .divide(BigDecimal.valueOf(billingDays), 4, RoundingMode.HALF_UP);

        List<RankedOffer> ranked = offers.stream()
                .map(o -> new RankedOffer(o, effectiveOfferPrice(o, weights)))
                .filter(r -> r.effectivePrice() != null)
                .sorted(Comparator.comparing(RankedOffer::effectivePrice))
                .toList();

        if (ranked.isEmpty()) return Optional.empty();

        List<RankedOffer> flatRanked = ranked.stream().filter(r -> !r.offer().isTou()).toList();
        List<RankedOffer> touRanked  = ranked.stream().filter(r ->  r.offer().isTou()).toList();

        ElectricityOfferBlock flatBlock = buildBlock(flatRanked, userPrice, annualKwh);
        ElectricityOfferBlock touBlock  = userIsTou ? null : buildBlock(touRanked, userPrice, annualKwh);

        if (flatBlock == null && touBlock == null) return Optional.empty();

        return Optional.of(new ElectricityComparisonResult(
                userPrice,
                userIsTou,
                annualKwh.setScale(2, RoundingMode.HALF_UP),
                flatBlock,
                touBlock,
                Instant.now()
        ));
    }

    private record RankedOffer(ElectricityMarketOffer offer, BigDecimal effectivePrice) {}

    private static boolean periodPricesDiffer(ElectricityFields f) {
        BigDecimal p1 = f.pricePerKwhP1();
        BigDecimal p2 = f.pricePerKwhP2();
        BigDecimal p3 = f.pricePerKwhP3();
        return (p3 != null && p1.compareTo(p3) != 0)
            || (p2 != null && p1.compareTo(p2) != 0);
    }

    /**
     * Period weights [w1, w2, w3] summing to 1.0.
     * Uses actual per-period consumption when available; falls back to a standard
     * residential profile (30/40/30 for 3-period, 60/0/40 for 2-period).
     */
    private static BigDecimal[] touWeights(ElectricityFields f) {
        BigDecimal c1 = f.consumptionKwhP1();
        BigDecimal c3 = f.consumptionKwhP3();
        if (c1 != null && c3 != null) {
            BigDecimal c2    = f.consumptionKwhP2() != null ? f.consumptionKwhP2() : BigDecimal.ZERO;
            BigDecimal total = c1.add(c2).add(c3);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                return new BigDecimal[]{
                    c1.divide(total, 6, RoundingMode.HALF_UP),
                    c2.divide(total, 6, RoundingMode.HALF_UP),
                    c3.divide(total, 6, RoundingMode.HALF_UP)
                };
            }
        }
        return f.pricePerKwhP2() != null
            ? new BigDecimal[]{BigDecimal.valueOf(0.30), BigDecimal.valueOf(0.40), BigDecimal.valueOf(0.30)}
            : new BigDecimal[]{BigDecimal.valueOf(0.60), BigDecimal.ZERO, BigDecimal.valueOf(0.40)};
    }

    private static BigDecimal effectiveUserPrice(ElectricityFields f, BigDecimal[] weights) {
        if (f.pricePerKwh() != null) return f.pricePerKwh();
        if (f.pricePerKwhP1() == null || f.pricePerKwhP3() == null) return null;
        BigDecimal p2 = f.pricePerKwhP2() != null ? f.pricePerKwhP2() : BigDecimal.ZERO;
        return f.pricePerKwhP1().multiply(weights[0])
                .add(p2.multiply(weights[1]))
                .add(f.pricePerKwhP3().multiply(weights[2]))
                .setScale(6, RoundingMode.HALF_UP);
    }

    // market: punta≈P1, llano≈P2, valle≈P3 — same weights as user side for a fair comparison
    private static BigDecimal effectiveOfferPrice(ElectricityMarketOffer offer, BigDecimal[] weights) {
        if (offer.pricePerKwh() != null) return offer.pricePerKwh();
        if (offer.pricePerKwhPunta() == null || offer.pricePerKwhValle() == null) return null;
        BigDecimal llano = offer.pricePerKwhLlano() != null ? offer.pricePerKwhLlano() : BigDecimal.ZERO;
        return offer.pricePerKwhPunta().multiply(weights[0])
                .add(llano.multiply(weights[1]))
                .add(offer.pricePerKwhValle().multiply(weights[2]))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static ElectricityOfferBlock buildBlock(
            List<RankedOffer> ranked, BigDecimal userPrice, BigDecimal annualKwh) {
        if (ranked.isEmpty()) return null;
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
        return new ElectricityOfferBlock(
                best.offer().company(),
                best.offer().tariffName(),
                best.effectivePrice(),
                annualSavings,
                alternatives
        );
    }
}
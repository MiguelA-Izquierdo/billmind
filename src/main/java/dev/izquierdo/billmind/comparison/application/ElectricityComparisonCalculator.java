package dev.izquierdo.billmind.comparison.application;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields.TaxBasis;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonBasis;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonBasis.ConsumptionProfile;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonBasis.PowerTerm;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityAlternativeRate;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ranks market offers against the user's invoice on <em>total annual cost</em> — energy plus the
 * power term — rather than on the energy price alone. The power term is a fixed cost per offer
 * that does not scale with consumption, so leaving it out did not merely shrink the saving: it
 * let an offer with cheap kWh and an expensive standing charge win a comparison it loses.
 *
 * <p>Taxes are applied once, to the difference. IEE and IVA are percentages on the base, so they
 * scale a saving without reordering the offers — which is why the ranking never needs them and
 * the headline figure always does.
 */
@Component
public class ElectricityComparisonCalculator {

    private static final Logger log = LoggerFactory.getLogger(ElectricityComparisonCalculator.class);

    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal ROUNDING_STEP = BigDecimal.TEN;
    private static final int MAX_ALTERNATIVES = 3;

    // Width of the band, by where the uncertainty comes from. These are the orders of magnitude of
    // Spanish residential seasonality and profile spread, not measurements — their job is to stop
    // the result from reading like one. Annualising a single winter or summer bill is by far the
    // largest source, which is why a period under three months dominates the rest.
    private static final BigDecimal U_SHORT_PERIOD    = new BigDecimal("0.25");
    private static final BigDecimal U_PART_YEAR       = new BigDecimal("0.12");
    private static final BigDecimal U_FULL_YEAR       = new BigDecimal("0.05");
    private static final BigDecimal U_ASSUMED_PROFILE = new BigDecimal("0.10");
    private static final BigDecimal U_DERIVED_POWER   = new BigDecimal("0.10");

    public Optional<ElectricityComparisonResult> calculate(
            ElectricityFields fields, List<ElectricityMarketOffer> offers) {

        if (offers.isEmpty()) return Optional.empty();
        Profile profile = buildProfile(fields);
        if (profile == null) return Optional.empty();
        if (profile.taxBasis() == TaxBasis.INCOHERENT) {
            // The parts do not add up to the printed total, so at least one extracted number is
            // wrong. Quoting a saving off it would be confident and false.
            log.warn("Extracted fields do not reconcile with the invoice total — reporting no comparison");
            return Optional.empty();
        }

        List<RankedOffer> ranked = rank(offers, profile);
        if (ranked.isEmpty()) return Optional.empty();

        // Both blocks are built whatever shape the user's own tariff has. The period block used to
        // be suppressed for a user already billed by periods, on the reading that it recommends a
        // kind of tariff; it recommends a named one, and moving from one period tariff to a cheaper
        // period tariff asks for no change of habits. That user is also the one whose invoice
        // carries real per-period consumption, so the comparison hidden from them was the only one
        // in the engine weighted by measured figures rather than an assumed profile.
        ElectricityOfferBlock flatBlock = buildBlock(filter(ranked, false), profile);
        ElectricityOfferBlock touBlock  = buildBlock(filter(ranked, true), profile);

        return Optional.of(new ElectricityComparisonResult(
                profile.userPrice(),
                profile.userIsTou(),
                profile.annualKwh().setScale(2, RoundingMode.HALF_UP),
                profile.userAnnualCost().setScale(2, RoundingMode.HALF_UP),
                fields.totalAmount(),
                profile.basis(),
                flatBlock,
                touBlock,
                Instant.now()
        ));
    }

    /** Everything the comparison needs about the user's own tariff, resolved once. */
    private record Profile(
            BigDecimal userPrice,
            boolean userIsTou,
            BigDecimal[] weights,
            BigDecimal periodKwh,
            BigDecimal annualKwh,
            BigDecimal contractedPowerKw,
            BigDecimal userPowerPricePerKwDay,
            BigDecimal userPeriodCost,
            BigDecimal userAnnualCost,
            TaxBasis taxBasis,
            ComparisonBasis basis
    ) {}

    private record RankedOffer(
            ElectricityMarketOffer offer,
            BigDecimal effectivePrice,
            BigDecimal periodCost,
            BigDecimal annualCost
    ) {}

    private record PowerResolution(BigDecimal pricePerKwDay, PowerTerm provenance) {}

    private static Profile buildProfile(ElectricityFields f) {
        long days = f.billingDays();
        if (days == 0 || f.consumptionKwh() == null) return null;

        BigDecimal[] weights   = touWeights(f);
        BigDecimal   userPrice = effectiveUserPrice(f, weights);
        if (userPrice == null) return null;

        BigDecimal annualKwh = f.consumptionKwh()
                .multiply(DAYS_PER_YEAR)
                .divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);

        TaxBasis        taxBasis = f.reconcileWithTotal();
        PowerResolution power    = resolvePower(f, taxBasis, days);
        ComparisonBasis basis    = new ComparisonBasis(days, days < 365, power.provenance(),
                hasActualPeriodConsumption(f) ? ConsumptionProfile.ACTUAL : ConsumptionProfile.ASSUMED,
                taxBasis != TaxBasis.POST_TAX);

        return new Profile(userPrice, f.pricePerKwhP1() != null && periodPricesDiffer(f), weights,
                f.consumptionKwh(), annualKwh,
                f.contractedPowerKw(), power.pricePerKwDay(),
                cost(f.consumptionKwh(), userPrice, f.contractedPowerKw(), power.pricePerKwDay(), days),
                cost(annualKwh, userPrice, f.contractedPowerKw(), power.pricePerKwDay(), 365),
                taxBasis, basis);
    }

    /**
     * The invoice line first; failing that, the residual left in the printed total once energy is
     * taken out. Only a pre-tax invoice can be solved that way — on a post-tax one the residual
     * would be the taxes, not the power term.
     */
    private static PowerResolution resolvePower(ElectricityFields f, TaxBasis basis, long days) {
        BigDecimal kw = f.contractedPowerKw();
        if (kw == null || kw.signum() <= 0) return new PowerResolution(null, PowerTerm.UNAVAILABLE);
        if (f.sumPowerPrices() != null) return new PowerResolution(f.sumPowerPrices(), PowerTerm.READ);
        if (basis != TaxBasis.PRE_TAX) return new PowerResolution(null, PowerTerm.UNAVAILABLE);

        BigDecimal cost = f.derivePowerCostForPeriod();
        if (cost == null) return new PowerResolution(null, PowerTerm.UNAVAILABLE);
        BigDecimal daily = cost.divide(kw.multiply(BigDecimal.valueOf(days)), 6, RoundingMode.HALF_UP);
        return new PowerResolution(daily, PowerTerm.DERIVED);
    }

    /**
     * Energy consumed plus the standing charge over the same days. The one cost function: both
     * sides of the comparison and both horizons go through it, so a saving over the billed period
     * and a saving over a year can never disagree about what they are measuring.
     */
    private static BigDecimal cost(BigDecimal kwh, BigDecimal pricePerKwh,
                                   BigDecimal contractedPowerKw, BigDecimal powerPricePerKwDay,
                                   long days) {
        BigDecimal energy = kwh.multiply(pricePerKwh);
        if (contractedPowerKw == null || powerPricePerKwDay == null) return energy;
        return energy.add(contractedPowerKw.multiply(powerPricePerKwDay).multiply(BigDecimal.valueOf(days)));
    }

    private static List<RankedOffer> rank(List<ElectricityMarketOffer> offers, Profile p) {
        return offers.stream()
                .map(o -> toRanked(o, p))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RankedOffer::annualCost))
                .toList();
    }

    private static RankedOffer toRanked(ElectricityMarketOffer offer, Profile p) {
        BigDecimal price = effectiveOfferPrice(offer, p.weights());
        if (price == null) return null;
        // An offer whose producer published no power term is costed at the user's own: unknown
        // means "this term does not change", never "this term is free" — which would win outright.
        BigDecimal powerPrice = offer.sumPowerPrices() != null
                ? offer.sumPowerPrices()
                : p.userPowerPricePerKwDay();
        return new RankedOffer(offer, price,
                cost(p.periodKwh(), price, p.contractedPowerKw(), powerPrice, p.basis().observedDays()),
                cost(p.annualKwh(), price, p.contractedPowerKw(), powerPrice, 365));
    }

    private static List<RankedOffer> filter(List<RankedOffer> ranked, boolean tou) {
        return ranked.stream().filter(r -> r.offer().isTou() == tou).toList();
    }

    private static ElectricityOfferBlock buildBlock(List<RankedOffer> ranked, Profile p) {
        if (ranked.isEmpty()) return null;
        RankedOffer best = ranked.get(0);

        BigDecimal savings = taxed(p.userAnnualCost().subtract(best.annualCost()), p.taxBasis());
        BigDecimal spread  = taxed(spread(p, best), p.taxBasis());

        // Nothing was extrapolated to reach this one, so it keeps its cents and gets no band.
        BigDecimal periodSavings = taxed(p.userPeriodCost().subtract(best.periodCost()), p.taxBasis())
                .setScale(2, RoundingMode.HALF_UP);

        return new ElectricityOfferBlock(
                best.offer().company(),
                best.offer().tariffName(),
                best.effectivePrice(),
                best.annualCost().setScale(2, RoundingMode.HALF_UP),
                periodSavings,
                roundToStep(savings.subtract(spread), RoundingMode.FLOOR),
                roundToStep(savings.add(spread), RoundingMode.CEILING),
                alternatives(ranked)
        );
    }

    private static List<ElectricityAlternativeRate> alternatives(List<RankedOffer> ranked) {
        return ranked.stream()
                .skip(1).limit(MAX_ALTERNATIVES)
                .map(r -> new ElectricityAlternativeRate(
                        r.offer().company(), r.offer().tariffName(), r.effectivePrice(),
                        r.annualCost().setScale(2, RoundingMode.HALF_UP), r.offer().isTou()))
                .toList();
    }

    /**
     * Half-width of the band. Only the energy half of the saving carries the annualisation error:
     * the power half is contracted kW × 365, which does not move with how much the user consumes.
     * A derived power term adds its own error on top, since solving it from the total absorbs the
     * fixed lines nobody extracts.
     */
    private static BigDecimal spread(Profile p, RankedOffer best) {
        BigDecimal energySavings = p.annualKwh().multiply(p.userPrice().subtract(best.effectivePrice()));
        BigDecimal width = energySavings.abs().multiply(uncertainty(p, best));
        if (p.basis().powerTerm() == PowerTerm.DERIVED) {
            BigDecimal annualPower = p.contractedPowerKw()
                    .multiply(p.userPowerPricePerKwDay())
                    .multiply(DAYS_PER_YEAR);
            width = width.add(annualPower.multiply(U_DERIVED_POWER));
        }
        return width;
    }

    /**
     * Annualising the observed period, plus the assumed period split — but the split only widens
     * anything when a time-of-use price is actually weighted by it. Comparing one flat price
     * against another never touches the weights, so charging it that uncertainty would inflate
     * the band of the case with the least of it.
     */
    private static BigDecimal uncertainty(Profile p, RankedOffer best) {
        long days = p.basis().observedDays();
        BigDecimal u = days < 90 ? U_SHORT_PERIOD : days < 300 ? U_PART_YEAR : U_FULL_YEAR;
        boolean weighted = p.userIsTou() || best.offer().isTou();
        return weighted && p.basis().consumptionProfile() == ConsumptionProfile.ASSUMED
                ? u.add(U_ASSUMED_PROFILE)
                : u;
    }

    /** Scales a pre-tax difference by IEE and IVA, unless the extracted prices already carried them. */
    private static BigDecimal taxed(BigDecimal preTax, TaxBasis basis) {
        return basis == TaxBasis.POST_TAX
                ? preTax
                : preTax.multiply(ElectricityFields.ORDINARY_TAX_FACTOR);
    }

    /** Rounds outwards to whole tens: a band that ends in cents is claiming a precision it lacks. */
    private static BigDecimal roundToStep(BigDecimal value, RoundingMode mode) {
        return value.divide(ROUNDING_STEP, 0, mode).multiply(ROUNDING_STEP).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean hasActualPeriodConsumption(ElectricityFields f) {
        return f.consumptionKwhP1() != null && f.consumptionKwhP3() != null;
    }

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
}
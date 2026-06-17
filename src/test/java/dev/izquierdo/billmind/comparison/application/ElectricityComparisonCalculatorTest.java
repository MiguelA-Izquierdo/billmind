package dev.izquierdo.billmind.comparison.application;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityMarketOffer;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ElectricityComparisonCalculatorTest {

    private final ElectricityComparisonCalculator calculator = new ElectricityComparisonCalculator();

    // 31-day billing period used across most tests
    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 2, 1);

    // ------------------------------------------------------------------ builders

    private static ElectricityFields flatFields(BigDecimal consumptionKwh, String pricePerKwh) {
        return new ElectricityFields(START, END, null, consumptionKwh,
                null, null, null,
                new BigDecimal(pricePerKwh), null, null, null, null);
    }

    /** TOU user — no flat price, per-period prices only, no per-period consumption breakdown. */
    private static ElectricityFields touPriceFields(BigDecimal consumptionKwh, String p1, String p2, String p3) {
        return new ElectricityFields(START, END, null, consumptionKwh,
                null, null, null,
                null, new BigDecimal(p1),
                p2 != null ? new BigDecimal(p2) : null,
                new BigDecimal(p3), null);
    }

    /** TOU user with per-period consumption breakdown (enables actual-consumption weighting). */
    private static ElectricityFields touFullFields(BigDecimal total,
                                                   BigDecimal c1, BigDecimal c2, BigDecimal c3,
                                                   String p1, String p2, String p3) {
        return new ElectricityFields(START, END, null, total,
                c1, c2, c3,
                null, new BigDecimal(p1),
                p2 != null ? new BigDecimal(p2) : null,
                new BigDecimal(p3), null);
    }

    private static ElectricityMarketOffer flatOffer(String company, String tariff, String price) {
        return new ElectricityMarketOffer(company, tariff, new BigDecimal(price),
                null, null, null, null);
    }

    private static ElectricityMarketOffer touOffer(String company, String tariff,
                                                   String punta, String llano, String valle) {
        return new ElectricityMarketOffer(company, tariff, null,
                new BigDecimal(valle),
                llano != null ? new BigDecimal(llano) : null,
                new BigDecimal(punta), null);
    }

    // ================================================================ guards

    @Test
    void shouldReturnEmptyWhenConsumptionKwhIsNull() {
        ElectricityFields fields = new ElectricityFields(
                START, END, null, null, null, null, null,
                new BigDecimal("0.18"), null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenOffersListIsEmpty() {
        assertThat(calculator.calculate(
                flatFields(new BigDecimal("200"), "0.18"), List.of())).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoPriceIsDerivable() {
        // neither pricePerKwh nor pricePerKwhP1/P3 → effectiveUserPrice returns null
        ElectricityFields fields = new ElectricityFields(
                START, END, null, new BigDecimal("200"), null, null, null,
                null, null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenBillingPeriodIsZeroDays() {
        ElectricityFields fields = new ElectricityFields(
                START, START, null, new BigDecimal("200"), null, null, null,
                new BigDecimal("0.18"), null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAllOffersHaveIncompletePricing() {
        // offer with punta but no valle → effectiveOfferPrice returns null → filtered out
        ElectricityMarketOffer noValle = new ElectricityMarketOffer(
                "X", "TX", null, null, null, new BigDecimal("0.20"), null);

        assertThat(calculator.calculate(
                flatFields(new BigDecimal("200"), "0.18"), List.of(noValle))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTouUserHasOnlyTouOffers() {
        // TOU user → touBlock suppressed; only TOU offers → flatBlock null → both null
        ElectricityFields fields = touPriceFields(
                new BigDecimal("200"), "0.22", "0.15", "0.10");
        List<ElectricityMarketOffer> offers = List.of(
                touOffer("A", "T", "0.20", "0.14", "0.09"));

        assertThat(calculator.calculate(fields, offers)).isEmpty();
    }

    // ================================================================ flat-rate user path

    @Test
    void shouldCalculateCorrectlyForFlatUserVsSingleFlatOffer() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("IBERDROLA", "2.0TD", "0.15"))).orElseThrow();

        // annualKwh = 200 × 365 / 31 = 2354.8387 → scale(2) = 2354.84
        assertThat(result.userPricePerKwh()).isEqualByComparingTo("0.18");
        assertThat(result.userIsTou()).isFalse();
        assertThat(result.annualKwhEstimate()).isEqualByComparingTo("2354.84");
        assertThat(result.comparedAt()).isNotNull();

        ElectricityOfferBlock flat = result.flatBlock();
        assertThat(flat).isNotNull();
        assertThat(flat.bestCompany()).isEqualTo("IBERDROLA");
        assertThat(flat.bestTariffName()).isEqualTo("2.0TD");
        assertThat(flat.bestPricePerKwh()).isEqualByComparingTo("0.15");
        // savings = (0.18 − 0.15) × 2354.8387 = 70.65
        assertThat(flat.annualSavingsEuros()).isEqualByComparingTo("70.65");
        assertThat(flat.alternatives()).isEmpty();

        // touBlock is null because no TOU offers exist in input
        assertThat(result.touBlock()).isNull();
    }

    @Test
    void shouldRankOffersFromCheapestToMostExpensive() {
        ElectricityFields fields = flatFields(new BigDecimal("100"), "0.20");
        List<ElectricityMarketOffer> offers = List.of(
                flatOffer("C", "T3", "0.17"),
                flatOffer("A", "T1", "0.14"),
                flatOffer("B", "T2", "0.16"));

        ElectricityOfferBlock flat = calculator.calculate(fields, offers).orElseThrow().flatBlock();

        assertThat(flat.bestCompany()).isEqualTo("A");
        assertThat(flat.bestPricePerKwh()).isEqualByComparingTo("0.14");
        assertThat(flat.alternatives()).hasSize(2);
        assertThat(flat.alternatives().get(0).company()).isEqualTo("B");
        assertThat(flat.alternatives().get(1).company()).isEqualTo("C");
    }

    @Test
    void shouldLimitAlternativesToThreeEvenWithMoreOffers() {
        ElectricityFields fields = flatFields(new BigDecimal("100"), "0.25");
        List<ElectricityMarketOffer> offers = List.of(
                flatOffer("A", "T1", "0.14"),
                flatOffer("B", "T2", "0.15"),
                flatOffer("C", "T3", "0.16"),
                flatOffer("D", "T4", "0.17"),
                flatOffer("E", "T5", "0.18"));

        ElectricityOfferBlock flat = calculator.calculate(fields, offers).orElseThrow().flatBlock();

        assertThat(flat.bestCompany()).isEqualTo("A");
        assertThat(flat.alternatives()).hasSize(3);
        assertThat(flat.alternatives().stream().map(a -> a.company()))
                .containsExactly("B", "C", "D");
    }

    @Test
    void shouldHaveNoAlternativesWhenOnlySingleOfferExists() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");

        ElectricityOfferBlock flat = calculator.calculate(
                fields, List.of(flatOffer("NATURGY", "LUZ-BASE", "0.15"))).orElseThrow().flatBlock();

        assertThat(flat.alternatives()).isEmpty();
    }

    @Test
    void shouldCalculateNegativeSavingsWhenUserAlreadyHasCheapestRate() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.12");

        ElectricityOfferBlock flat = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.15"))).orElseThrow().flatBlock();

        assertThat(flat.annualSavingsEuros()).isNegative();
    }

    @Test
    void shouldBuildTouBlockForFlatUserWhenTouOffersExist() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        List<ElectricityMarketOffer> offers = List.of(
                flatOffer("FLAT_CO", "FLAT-T", "0.16"),
                touOffer("TOU_CO", "TOU-T", "0.22", "0.15", "0.09"));

        ElectricityComparisonResult result = calculator.calculate(fields, offers).orElseThrow();

        assertThat(result.flatBlock()).isNotNull();
        assertThat(result.touBlock()).isNotNull();
        assertThat(result.touBlock().bestCompany()).isEqualTo("TOU_CO");
    }

    @Test
    void shouldReturnResultWithOnlyTouBlockWhenFlatUserHasOnlyTouOffers() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        List<ElectricityMarketOffer> offers = List.of(
                touOffer("TOU_CO", "TOU-T", "0.22", "0.15", "0.09"));

        ElectricityComparisonResult result = calculator.calculate(fields, offers).orElseThrow();

        assertThat(result.flatBlock()).isNull();
        assertThat(result.touBlock()).isNotNull();
    }

    // ================================================================ TOU user path

    @Test
    void shouldDetectTouUserWhenP1AndP3PricesDiffer() {
        ElectricityFields fields = touPriceFields(
                new BigDecimal("200"), "0.22", "0.15", "0.10");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.15"))).orElseThrow();

        assertThat(result.userIsTou()).isTrue();
    }

    @Test
    void shouldDetectTouUserWhenOnlyP1AndP2PricesDiffer() {
        // P1 ≠ P2 is sufficient — P3 equals P1
        ElectricityFields fields = new ElectricityFields(
                START, END, null, new BigDecimal("200"), null, null, null,
                null, new BigDecimal("0.22"), new BigDecimal("0.15"), new BigDecimal("0.22"), null);

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.15"))).orElseThrow();

        assertThat(result.userIsTou()).isTrue();
    }

    @Test
    void shouldNotDetectTouWhenAllPeriodPricesAreEqual() {
        ElectricityFields fields = new ElectricityFields(
                START, END, null, new BigDecimal("200"), null, null, null,
                null, new BigDecimal("0.18"), new BigDecimal("0.18"), new BigDecimal("0.18"), null);

        assertThat(calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.15"))).orElseThrow().userIsTou()).isFalse();
    }

    @Test
    void shouldSuppressTouBlockForTouUser() {
        ElectricityFields fields = touPriceFields(
                new BigDecimal("200"), "0.22", "0.15", "0.10");
        List<ElectricityMarketOffer> offers = List.of(
                flatOffer("FLAT_CO", "FLAT-T", "0.16"),
                touOffer("TOU_CO", "TOU-T", "0.20", "0.13", "0.08"));

        ElectricityComparisonResult result = calculator.calculate(fields, offers).orElseThrow();

        assertThat(result.userIsTou()).isTrue();
        assertThat(result.touBlock()).isNull();
        assertThat(result.flatBlock()).isNotNull();
    }

    // ================================================================ TOU weight calculation

    @Test
    void shouldUseActualConsumptionWeightsWhenPerPeriodDataAvailable() {
        // c1=100, c2=200, c3=100 → total=400 → weights 25/50/25
        // effective = 0.20×0.25 + 0.15×0.50 + 0.10×0.25 = 0.05 + 0.075 + 0.025 = 0.15
        ElectricityFields fields = touFullFields(
                new BigDecimal("400"),
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("100"),
                "0.20", "0.15", "0.10");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.12"))).orElseThrow();

        assertThat(result.userPricePerKwh()).isEqualByComparingTo("0.150000");
    }

    @Test
    void shouldUseThreePeriodProfileWhenP2PresentButNoPerPeriodConsumption() {
        // standard 3-period profile: weights 30/40/30
        // effective = 0.20×0.30 + 0.15×0.40 + 0.10×0.30 = 0.06 + 0.06 + 0.03 = 0.15
        ElectricityFields fields = touPriceFields(
                new BigDecimal("200"), "0.20", "0.15", "0.10");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.12"))).orElseThrow();

        assertThat(result.userPricePerKwh()).isEqualByComparingTo("0.150000");
    }

    @Test
    void shouldUseTwoPeriodProfileWhenP2IsAbsent() {
        // standard 2-period profile: weights 60/0/40
        // effective = 0.20×0.60 + 0×0 + 0.10×0.40 = 0.12 + 0.04 = 0.16
        ElectricityFields fields = touPriceFields(
                new BigDecimal("200"), "0.20", null, "0.10");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.12"))).orElseThrow();

        assertThat(result.userPricePerKwh()).isEqualByComparingTo("0.160000");
    }

    @Test
    void shouldFallbackToStandardProfileWhenPerPeriodConsumptionTotalsZero() {
        // c1=0, c3=0 → total=0 → condition `total > 0` is false → falls back to 3-period profile
        // effective = 0.20×0.30 + 0.15×0.40 + 0.10×0.30 = 0.15
        ElectricityFields fields = touFullFields(
                new BigDecimal("0"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "0.20", "0.15", "0.10");

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.12"))).orElseThrow();

        assertThat(result.userPricePerKwh()).isEqualByComparingTo("0.150000");
    }

    // ================================================================ annual kWh extrapolation

    @Test
    void shouldExtrapolateAnnualKwhFrom31DayBillingPeriod() {
        // 200 × 365 / 31 = 2354.8387... → scale(2) = 2354.84
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))
                .orElseThrow().annualKwhEstimate())
                .isEqualByComparingTo("2354.84");
    }

    @Test
    void shouldExtrapolateAnnualKwhFrom7DayBillingPeriod() {
        // 70 × 365 / 7 = 3650.00
        ElectricityFields fields = new ElectricityFields(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8),
                null, new BigDecimal("70"), null, null, null,
                new BigDecimal("0.18"), null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))
                .orElseThrow().annualKwhEstimate())
                .isEqualByComparingTo("3650.00");
    }

    // ================================================================ offer price calculation

    @Test
    void shouldUseFlatOfferPriceDirectlyWithoutWeighting() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");

        ElectricityOfferBlock flat = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.150000"))).orElseThrow().flatBlock();

        assertThat(flat.bestPricePerKwh()).isEqualByComparingTo("0.150000");
    }

    @Test
    void shouldTreatMissingLlanoAsZeroInTouOfferWeighting() {
        // Flat user, 2-period weights (no P2): 0.60/0/0.40
        // offer: punta=0.21, llano=null→0, valle=0.09
        // effective = 0.21×0.60 + 0×0 + 0.09×0.40 = 0.126 + 0.036 = 0.162
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        ElectricityMarketOffer noLlano = touOffer("A", "TOU-2P", "0.21", null, "0.09");

        Optional<ElectricityComparisonResult> result = calculator.calculate(fields, List.of(noLlano));

        assertThat(result).isPresent();
        assertThat(result.get().touBlock().bestPricePerKwh()).isEqualByComparingTo("0.162000");
    }

    @Test
    void shouldFilterOutTouOfferMissingValle() {
        // punta present but valle absent → effectiveOfferPrice returns null → filtered
        ElectricityMarketOffer noValle = new ElectricityMarketOffer(
                "X", "TX", null, null, null, new BigDecimal("0.20"), null);

        assertThat(calculator.calculate(
                flatFields(new BigDecimal("200"), "0.18"), List.of(noValle))).isEmpty();
    }

    // ================================================================ ComparisonResult delegation

    @Test
    void shouldExposeTopFlatBlockViaResultMethods() {
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        List<ElectricityMarketOffer> offers = List.of(
                flatOffer("FLAT_CO", "FLAT-T", "0.16"),
                touOffer("TOU_CO", "TOU-T", "0.22", "0.15", "0.09"));

        ElectricityComparisonResult result = calculator.calculate(fields, offers).orElseThrow();

        assertThat(result.bestCompany()).isEqualTo("FLAT_CO");
        assertThat(result.bestTariffName()).isEqualTo("FLAT-T");
        assertThat(result.annualSavingsEuros()).isPositive();
    }

    @Test
    void shouldFallbackToTouBlockWhenFlatBlockIsNull() {
        // flat user + only TOU offers → flatBlock null, touBlock present
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(touOffer("TOU_CO", "TOU-T", "0.22", "0.15", "0.09"))).orElseThrow();

        assertThat(result.flatBlock()).isNull();
        assertThat(result.bestCompany()).isEqualTo("TOU_CO");
        assertThat(result.annualSavingsEuros()).isNotNull();
    }
}
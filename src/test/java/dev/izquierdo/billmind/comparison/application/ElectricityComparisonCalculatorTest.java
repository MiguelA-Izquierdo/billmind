package dev.izquierdo.billmind.comparison.application;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonBasis;
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
                new BigDecimal(pricePerKwh), null, null, null, null, null, null);
    }

    /** TOU user — no flat price, per-period prices only, no per-period consumption breakdown. */
    private static ElectricityFields touPriceFields(BigDecimal consumptionKwh, String p1, String p2, String p3) {
        return new ElectricityFields(START, END, null, consumptionKwh,
                null, null, null,
                null, new BigDecimal(p1),
                p2 != null ? new BigDecimal(p2) : null,
                new BigDecimal(p3), null, null, null);
    }

    /** TOU user with per-period consumption breakdown (enables actual-consumption weighting). */
    private static ElectricityFields touFullFields(BigDecimal total,
                                                   BigDecimal c1, BigDecimal c2, BigDecimal c3,
                                                   String p1, String p2, String p3) {
        return new ElectricityFields(START, END, null, total,
                c1, c2, c3,
                null, new BigDecimal(p1),
                p2 != null ? new BigDecimal(p2) : null,
                new BigDecimal(p3), null, null, null);
    }

    /** Flat user with a contracted power and its price — the full annual cost path. */
    private static ElectricityFields flatFieldsWithPower(BigDecimal consumptionKwh, String pricePerKwh,
                                                         String kw, String powerP1, String powerP2) {
        return new ElectricityFields(START, END, null, consumptionKwh,
                null, null, null,
                new BigDecimal(pricePerKwh), null, null, null, new BigDecimal(kw),
                new BigDecimal(powerP1), powerP2 != null ? new BigDecimal(powerP2) : null);
    }

    private static ElectricityMarketOffer flatOffer(String company, String tariff, String price) {
        return new ElectricityMarketOffer(company, tariff, new BigDecimal(price),
                null, null, null, null, null);
    }

    private static ElectricityMarketOffer flatOfferWithPower(String company, String tariff,
                                                             String price, String powerP1, String powerP2) {
        return new ElectricityMarketOffer(company, tariff, new BigDecimal(price),
                null, null, null,
                new BigDecimal(powerP1), powerP2 != null ? new BigDecimal(powerP2) : null);
    }

    private static ElectricityMarketOffer touOffer(String company, String tariff,
                                                   String punta, String llano, String valle) {
        return new ElectricityMarketOffer(company, tariff, null,
                new BigDecimal(valle),
                llano != null ? new BigDecimal(llano) : null,
                new BigDecimal(punta), null, null);
    }

    // ================================================================ guards

    @Test
    void shouldReturnEmptyWhenConsumptionKwhIsNull() {
        ElectricityFields fields = new ElectricityFields(
                START, END, null, null, null, null, null,
                new BigDecimal("0.18"), null, null, null, null, null, null);

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
                null, null, null, null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenBillingPeriodIsZeroDays() {
        ElectricityFields fields = new ElectricityFields(
                START, START, null, new BigDecimal("200"), null, null, null,
                new BigDecimal("0.18"), null, null, null, null, null, null);

        assertThat(calculator.calculate(fields, List.of(flatOffer("A", "T", "0.15")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAllOffersHaveIncompletePricing() {
        // offer with punta but no valle → effectiveOfferPrice returns null → filtered out
        ElectricityMarketOffer noValle = new ElectricityMarketOffer(
                "X", "TX", null, null, null, new BigDecimal("0.20"), null, null);

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
        // energy saving = (0.18 − 0.15) × 2354.8387 = 70.65, × 1.27186 tax = 89.85.
        // 31 days invoiced → ±25 % on the energy half = ±22.46, rounded outwards to whole tens.
        assertThat(flat.annualSavingsLow()).isEqualByComparingTo("60.00");
        assertThat(flat.annualSavingsHigh()).isEqualByComparingTo("120.00");
        assertThat(flat.annualSavingsMid()).isEqualByComparingTo("90.00");
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

        assertThat(flat.annualSavingsLow()).isNegative();
        assertThat(flat.annualSavingsHigh()).isNegative();
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
                null, new BigDecimal("0.22"), new BigDecimal("0.15"), new BigDecimal("0.22"), null, null, null);

        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(flatOffer("A", "T", "0.15"))).orElseThrow();

        assertThat(result.userIsTou()).isTrue();
    }

    @Test
    void shouldNotDetectTouWhenAllPeriodPricesAreEqual() {
        ElectricityFields fields = new ElectricityFields(
                START, END, null, new BigDecimal("200"), null, null, null,
                null, new BigDecimal("0.18"), new BigDecimal("0.18"), new BigDecimal("0.18"), null, null, null);

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
                new BigDecimal("0.18"), null, null, null, null, null, null);

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
                "X", "TX", null, null, null, new BigDecimal("0.20"), null, null);

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
        assertThat(result.annualSavingsHighEuros()).isPositive();
    }

    @Test
    void shouldFallbackToTouBlockWhenFlatBlockIsNull() {
        // flat user + only TOU offers → flatBlock null, touBlock present
        ElectricityFields fields = flatFields(new BigDecimal("200"), "0.18");
        ElectricityComparisonResult result = calculator.calculate(
                fields, List.of(touOffer("TOU_CO", "TOU-T", "0.22", "0.15", "0.09"))).orElseThrow();

        assertThat(result.flatBlock()).isNull();
        assertThat(result.bestCompany()).isEqualTo("TOU_CO");
        assertThat(result.annualSavingsLowEuros()).isNotNull();
    }

    // ================================================================ power term

    /**
     * The defect the power term was added for. Ranking on €/kWh alone hands the win to the offer
     * with the cheaper energy even when its standing charge costs the user far more than the
     * energy saves — and the standing charge is the half that does not shrink with consumption.
     */
    @Test
    void shouldRankOnTotalCostSoCheapEnergyWithExpensivePowerLoses() {
        ElectricityFields fields = flatFieldsWithPower(
                new BigDecimal("300"), "0.20", "4.6", "0.100000", "0.020000");
        ElectricityMarketOffer cheapEnergy = flatOfferWithPower(
                "CHEAP_KWH", "T1", "0.150000", "0.180000", "0.030000");
        ElectricityMarketOffer cheapPower = flatOfferWithPower(
                "CHEAP_POWER", "T2", "0.160000", "0.090000", "0.020000");

        ElectricityOfferBlock flat = calculator
                .calculate(fields, List.of(cheapEnergy, cheapPower)).orElseThrow().flatBlock();

        // energy: CHEAP_KWH wins by 35 €/yr. power: CHEAP_POWER wins by 168 €/yr. Total decides.
        assertThat(flat.bestCompany()).isEqualTo("CHEAP_POWER");
        assertThat(flat.bestAnnualCostEuros())
                .isLessThan(flat.alternatives().get(0).annualCostEuros());
    }

    /** A saving can come entirely from the standing charge — with identical energy prices. */
    @Test
    void shouldReportSavingsFromThePowerTermAloneWhenEnergyPricesMatch() {
        ElectricityFields fields = flatFieldsWithPower(
                new BigDecimal("300"), "0.150000", "4.6", "0.100000", "0.020000");

        ElectricityOfferBlock flat = calculator.calculate(fields,
                List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.010000")))
                .orElseThrow().flatBlock();

        // 4.6 kW × 0.02 €/kW/día × 365 = 33.58 €, × 1.27186 tax = 42.71 €. No energy delta, so
        // the annualisation error has nothing to act on and the band stays one step wide.
        assertThat(flat.annualSavingsLow()).isEqualByComparingTo("40.00");
        assertThat(flat.annualSavingsHigh()).isEqualByComparingTo("50.00");
    }

    /**
     * An offer whose producer published no power term must not be treated as if that term were
     * free — it would win every comparison outright. Costed at the user's own, an identical
     * energy price yields no saving.
     */
    @Test
    void shouldCostAnOfferWithNoPowerTermAtTheUsersOwnRate() {
        ElectricityFields fields = flatFieldsWithPower(
                new BigDecimal("300"), "0.150000", "4.6", "0.100000", "0.020000");

        ElectricityOfferBlock flat = calculator
                .calculate(fields, List.of(flatOffer("A", "T", "0.150000"))).orElseThrow().flatBlock();

        assertThat(flat.annualSavingsLow()).isEqualByComparingTo("0.00");
        assertThat(flat.annualSavingsHigh()).isEqualByComparingTo("0.00");
    }

    // ================================================================ reconciliation gate

    /**
     * Figures from a real Endesa 2.0TD bill (32 days, 419,475 kWh, 135,64 € total). The parts add
     * up to the printed total once tax is allowed for, so the comparison proceeds with the power
     * term read off the invoice.
     */
    @Test
    void shouldCompareRealInvoiceWhosePartsReconcileWithItsTotal() {
        ElectricityFields fields = realInvoice("0.217764", "0.102630", "0.022452");

        ElectricityComparisonResult result = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))
                .orElseThrow();

        assertThat(result.basis().observedDays()).isEqualTo(32);
        assertThat(result.basis().powerTerm()).isEqualTo(ComparisonBasis.PowerTerm.READ);
        assertThat(result.basis().taxesIncluded()).isTrue();
        assertThat(result.basis().annualised()).isTrue();
    }

    /**
     * The same bill with the energy price misread as 0,118 instead of 0,218 — a plausible-looking
     * number on its own. Against the printed total it is not: the parts fall 47 € short of what
     * tax alone could explain, so no saving is quoted at all.
     */
    @Test
    void shouldRefuseToCompareWhenTheExtractedPriceDoesNotReconcile() {
        ElectricityFields fields = realInvoice("0.118000", "0.102630", "0.022452");

        assertThat(fields.reconcileWithTotal()).isEqualTo(ElectricityFields.TaxBasis.INCOHERENT);
        assertThat(calculator.calculate(fields,
                List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))).isEmpty();
    }

    /** Power line unread: solved from the total instead of dropped, and marked as derived. */
    @Test
    void shouldDerivePowerTermFromTheTotalWhenTheInvoiceLineIsMissing() {
        ElectricityFields fields = realInvoice("0.217764", null, null);

        ElectricityComparisonResult result = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))
                .orElseThrow();

        assertThat(result.basis().powerTerm()).isEqualTo(ComparisonBasis.PowerTerm.DERIVED);
        assertThat(result.userAnnualCostEuros()).isGreaterThan(new BigDecimal("1000"));
    }

    /** Prices that already carry tax must not be taxed twice — that was a free 27 % of saving. */
    @Test
    void shouldNotApplyTaxAgainWhenTheExtractedPricesAlreadyIncludeIt() {
        ElectricityFields fields = realInvoice("0.277000", "0.130000", "0.029000");

        ElectricityComparisonResult result = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.200000", "0.100000", "0.020000")))
                .orElseThrow();

        assertThat(fields.reconcileWithTotal()).isEqualTo(ElectricityFields.TaxBasis.POST_TAX);
        assertThat(result.basis().taxesIncluded()).isFalse();
    }

    // ================================================================ billed-period saving

    /**
     * The only figure on the card the user can check: real consumption, real days, no
     * extrapolation. It keeps its cents precisely because there is no uncertainty to round away,
     * and it must stay consistent with the annual projection it sits above — the same cost
     * function over 32 days instead of 365.
     */
    @Test
    void shouldReportTheBilledPeriodSavingWithoutExtrapolatingOrRounding() {
        ElectricityFields fields = realInvoice("0.217764", "0.102630", "0.022452");

        ElectricityComparisonResult result = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))
                .orElseThrow();
        ElectricityOfferBlock flat = result.flatBlock();

        // energía 419,475 × (0,217764 − 0,150000) = 28,43 €
        // potencia 3,45 × (0,125082 − 0,110000) × 32 días = 1,67 €
        // (28,43 + 1,67) × 1,27186 = 38,27 €
        assertThat(flat.periodSavingsEuros()).isEqualByComparingTo("38.27");
        assertThat(result.invoiceTotalEuros()).isEqualByComparingTo("135.64");
    }

    /** Both horizons come from one cost function, so the period is the year scaled by its days. */
    @Test
    void shouldKeepThePeriodSavingConsistentWithTheAnnualProjection() {
        ElectricityFields fields = realInvoice("0.217764", "0.102630", "0.022452");

        ElectricityOfferBlock flat = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))
                .orElseThrow().flatBlock();

        BigDecimal scaledToYear = flat.periodSavingsEuros()
                .multiply(new BigDecimal("365"))
                .divide(new BigDecimal("32"), 2, java.math.RoundingMode.HALF_UP);

        assertThat(scaledToYear)
                .isBetween(flat.annualSavingsLow(), flat.annualSavingsHigh());
    }

    /** A user already cheaper than the market would pay more, and the period figure says so. */
    @Test
    void shouldReportANegativePeriodSavingWhenTheOfferIsDearer() {
        ElectricityFields fields = flatFieldsWithPower(
                new BigDecimal("300"), "0.100000", "4.6", "0.100000", "0.020000");

        ElectricityOfferBlock flat = calculator
                .calculate(fields, List.of(flatOfferWithPower("A", "T", "0.150000", "0.090000", "0.020000")))
                .orElseThrow().flatBlock();

        assertThat(flat.periodSavingsEuros()).isNegative();
    }

    /** A real Endesa 2.0TD invoice: 10/06/2026–12/07/2026, 419,475 kWh, 3,45 kW, 135,64 € total. */
    private static ElectricityFields realInvoice(String pricePerKwh, String powerP1, String powerP2) {
        return new ElectricityFields(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 12),
                new BigDecimal("135.64"), new BigDecimal("419.475"),
                null, null, null,
                new BigDecimal(pricePerKwh), null, null, null, new BigDecimal("3.45"),
                powerP1 != null ? new BigDecimal(powerP1) : null,
                powerP2 != null ? new BigDecimal(powerP2) : null);
    }
}
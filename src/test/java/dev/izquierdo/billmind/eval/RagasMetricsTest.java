package dev.izquierdo.billmind.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Pure unit tests for the deterministic RAGAS scoring functions and the judge parser. */
class RagasMetricsTest {

    // --- contextPrecision (Average Precision @k) ---

    @Test
    void shouldGivePerfectPrecisionWhenAllRelevantRankedFirst() {
        double p = RagasMetrics.contextPrecision(
                List.of("GLOSSARY"),
                List.of("GLOSSARY", "GLOSSARY", "GENERAL"));
        assertThat(p).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeRelevantChunkRankedLow() {
        // relevant only at rank 3 → precision 1/3
        double p = RagasMetrics.contextPrecision(
                List.of("GLOSSARY"),
                List.of("GENERAL", "BOE_REGULATION", "GLOSSARY"));
        assertThat(p).isCloseTo(1.0 / 3.0, within(1e-9));
    }

    @Test
    void shouldReturnZeroPrecisionWhenNothingRelevant() {
        double p = RagasMetrics.contextPrecision(
                List.of("GLOSSARY"),
                List.of("GENERAL", "BOE_REGULATION"));
        assertThat(p).isZero();
    }

    @Test
    void shouldReturnZeroPrecisionWhenNothingRetrieved() {
        double p = RagasMetrics.contextPrecision(List.of("GLOSSARY"), List.of());
        assertThat(p).isZero();
    }

    @Test
    void shouldAverageAcrossMultipleRelevantHits() {
        // relevant at ranks 1 and 3 → (1/1 + 2/3) / 2 = 0.8333
        double p = RagasMetrics.contextPrecision(
                List.of("GLOSSARY"),
                List.of("GLOSSARY", "GENERAL", "GLOSSARY"));
        assertThat(p).isCloseTo((1.0 + 2.0 / 3.0) / 2.0, within(1e-9));
    }

    // --- contextRecall ---

    @Test
    void shouldHitRecallWhenExpectedDocTypePresent() {
        assertThat(RagasMetrics.contextRecall(List.of("GLOSSARY", "REE_GUIDE"),
                List.of("GENERAL", "GLOSSARY"))).isEqualTo(1.0);
    }

    @Test
    void shouldMissRecallWhenExpectedDocTypeAbsent() {
        assertThat(RagasMetrics.contextRecall(List.of("GLOSSARY"),
                List.of("GENERAL", "BOE_REGULATION"))).isZero();
    }

    // --- factCoverage ---

    @Test
    void shouldCoverAllFactsAccentAndCaseInsensitive() {
        double c = RagasMetrics.factCoverage(
                List.of("CUPS", "punto de suministro"),
                "El cups identifica el PUNTO DE SUMINISTRÓ eléctrico.");
        assertThat(c).isEqualTo(1.0);
    }

    @Test
    void shouldReportPartialFactCoverage() {
        double c = RagasMetrics.factCoverage(
                List.of("5,11269", "IEE", "base imponible"),
                "El IEE se aplica sobre la base imponible.");
        assertThat(c).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void shouldReturnFullCoverageWhenNoFactsRequired() {
        assertThat(RagasMetrics.factCoverage(List.of(), "cualquier cosa")).isEqualTo(1.0);
    }

    // --- cosine ---

    @Test
    void shouldComputeCosineOfIdenticalVectorsAsOne() {
        float[] v = {0.1f, 0.2f, 0.3f};
        assertThat(EvalEmbeddings.cosine(v, v)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void shouldComputeCosineOfOrthogonalVectorsAsZero() {
        assertThat(EvalEmbeddings.cosine(new float[]{1f, 0f}, new float[]{0f, 1f})).isZero();
    }

    @Test
    void shouldReturnZeroCosineForZeroVector() {
        assertThat(EvalEmbeddings.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
    }

    // --- LlmEvalJudge parser ---

    @Test
    void shouldParseFaithfulnessRatio() {
        assertThat(LlmEvalJudge.parseRatio("SUPPORTED=3 TOTAL=4")).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void shouldParseRatioWithSurroundingNoise() {
        assertThat(LlmEvalJudge.parseRatio("Result: SUPPORTED=2, TOTAL=2.")).isEqualTo(1.0);
    }

    @Test
    void shouldTreatZeroTotalAsFullyFaithful() {
        assertThat(LlmEvalJudge.parseRatio("SUPPORTED=0 TOTAL=0")).isEqualTo(1.0);
    }

    @Test
    void shouldReturnNaNWhenUnparseable() {
        assertThat(LlmEvalJudge.parseRatio("no idea")).isNaN();
    }
}
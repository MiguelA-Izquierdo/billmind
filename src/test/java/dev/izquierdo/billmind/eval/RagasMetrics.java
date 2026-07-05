package dev.izquierdo.billmind.eval;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Pure, deterministic RAGAS-style scoring functions. No LLM, no Spring — every method
 * is a total function of its inputs and is unit-tested in {@code RagasMetricsTest}.
 *
 * <p>The embedding-based metrics (answer relevancy, reference coverage, faithfulness proxy)
 * live in the harness itself since they depend on the {@link EvalEmbeddings} model; this
 * class holds the label/rank/string metrics that need no model.
 */
public final class RagasMetrics {

    private RagasMetrics() {}

    /**
     * Context precision as Average Precision @k: rewards ranking relevant chunks (those whose
     * docType is expected) near the top. Returns 0 when nothing relevant was retrieved, 1 when
     * every relevant chunk precedes every irrelevant one.
     *
     * @param expectedDocTypes         docTypes considered relevant for the question
     * @param retrievedDocTypesInOrder retrieved chunk docTypes, best rank first
     */
    public static double contextPrecision(List<String> expectedDocTypes,
                                          List<String> retrievedDocTypesInOrder) {
        if (retrievedDocTypesInOrder.isEmpty()) return 0.0;
        int relevantSoFar = 0;
        double precisionSum = 0.0;
        for (int i = 0; i < retrievedDocTypesInOrder.size(); i++) {
            if (expectedDocTypes.contains(retrievedDocTypesInOrder.get(i))) {
                relevantSoFar++;
                precisionSum += (double) relevantSoFar / (i + 1);
            }
        }
        return relevantSoFar == 0 ? 0.0 : precisionSum / relevantSoFar;
    }

    /**
     * Context recall (binary, per case): 1.0 if at least one expected docType was retrieved in
     * the top-k, else 0.0. Averaging across the golden set yields recall@k.
     */
    public static double contextRecall(List<String> expectedDocTypes,
                                       List<String> retrievedDocTypes) {
        return retrievedDocTypes.stream().anyMatch(expectedDocTypes::contains) ? 1.0 : 0.0;
    }

    /**
     * Fraction of {@code mustIncludeFacts} present in the answer, matched accent- and
     * case-insensitively as substrings. Returns 1.0 when there are no required facts.
     */
    public static double factCoverage(List<String> mustIncludeFacts, String answer) {
        if (mustIncludeFacts.isEmpty()) return 1.0;
        String normalizedAnswer = normalize(answer);
        long matched = mustIncludeFacts.stream()
                .filter(fact -> normalizedAnswer.contains(normalize(fact)))
                .count();
        return (double) matched / mustIncludeFacts.size();
    }

    /** Lowercase and strip diacritics so "P3 valle" matches "p3 válle", etc. */
    static String normalize(String s) {
        if (s == null) return "";
        String stripped = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT);
    }
}
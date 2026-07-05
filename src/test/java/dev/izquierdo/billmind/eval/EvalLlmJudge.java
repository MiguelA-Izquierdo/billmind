package dev.izquierdo.billmind.eval;

import java.util.List;

/**
 * LLM-as-judge layer of the hybrid harness. Only exercised when an evaluation LLM is
 * available (see {@code AssistantRagEvalIT}); the deterministic metrics never need it.
 */
public interface EvalLlmJudge {

    /**
     * Faithfulness / groundedness: fraction of the answer's atomic claims that are supported
     * by the retrieved context. 1.0 means fully grounded, 0.0 means hallucinated.
     * Returns {@link Double#NaN} if the judge response cannot be parsed.
     */
    double faithfulness(String answer, List<String> contexts);
}
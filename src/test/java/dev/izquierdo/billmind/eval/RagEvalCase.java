package dev.izquierdo.billmind.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

/**
 * One entry of the RAGAS golden set. Loaded from
 * {@code src/test/resources/eval/rag_eval_dataset.json}.
 *
 * @param question         the user question posed to the assistant
 * @param referenceAnswer  curated ground-truth answer (Spanish) used by the deterministic
 *                         coverage metrics and by the LLM-judge as a reference
 * @param expectedDocTypes {@code DocType} names the retrieval is expected to surface
 * @param mustIncludeFacts distinctive facts/keywords a correct answer should contain
 * @param difficulty       BAJA / MEDIA / ALTA
 */
public record RagEvalCase(
        String id,
        String question,
        String referenceAnswer,
        List<String> expectedDocTypes,
        List<String> mustIncludeFacts,
        String difficulty
) {
    private static final String DATASET = "eval/rag_eval_dataset.json";

    public static List<RagEvalCase> loadAll() {
        try {
            return new ObjectMapper().readValue(
                    new ClassPathResource(DATASET).getInputStream(),
                    new TypeReference<List<RagEvalCase>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load RAG eval dataset from " + DATASET, e);
        }
    }
}
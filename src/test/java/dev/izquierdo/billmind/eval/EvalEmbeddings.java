package dev.izquierdo.billmind.eval;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Thin wrapper over the configured {@link EmbeddingModel} exposing cosine similarity,
 * used by the deterministic (embedding-based) RAGAS metrics. Uses the same embedding
 * model as production retrieval (AllMiniLM-L6-v2 in tests), so similarity scores are
 * on the same scale as the vector store.
 */
public final class EvalEmbeddings {

    private final EmbeddingModel model;

    public EvalEmbeddings(EmbeddingModel model) {
        this.model = model;
    }

    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    /** Cosine similarity between the embeddings of two texts, in [-1, 1] (0 if either is empty). */
    public double similarity(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return 0.0;
        return cosine(embed(a), embed(b));
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
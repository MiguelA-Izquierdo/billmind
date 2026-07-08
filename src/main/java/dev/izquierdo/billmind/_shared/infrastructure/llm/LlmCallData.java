package dev.izquierdo.billmind._shared.infrastructure.llm;

import java.time.Instant;

/**
 * Immutable snapshot of a single LLM call, assembled by {@link TimedChatLanguageModel} and handed
 * to every {@link LlmTelemetry} sink (metrics, tracing). Carries what both the Micrometer meters
 * and the OTLP span need: routing tags, timing, token usage and the already-computed USD cost.
 * Token fields and {@code costUsd} are nullable (usage may be absent or the model unpriced);
 * {@code error} is null on success.
 */
public record LlmCallData(
        String operation,
        String type,
        String role,
        String provider,
        String model,
        Instant startedAt,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        Integer tokensTotal,
        Double costUsd,
        String error) {

    public boolean isError() {
        return error != null;
    }

    /** Low-cardinality outcome tag for metrics and span status. */
    public String outcome() {
        return error == null ? "success" : "error";
    }
}
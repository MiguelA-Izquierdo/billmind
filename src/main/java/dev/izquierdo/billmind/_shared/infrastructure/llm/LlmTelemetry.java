package dev.izquierdo.billmind._shared.infrastructure.llm;

import java.util.List;

/**
 * Sink for per-call LLM telemetry. Implementations fan a {@link LlmCallData} out to a concrete
 * backend — Micrometer meters ({@link MetricsLlmTelemetry}) or an OTLP tracer
 * ({@link TracingLlmTelemetry}). Each sink is independently toggled by its own property, so a
 * deployment may run neither, either, or both. {@link TimedChatLanguageModel} holds exactly one
 * {@code LlmTelemetry}; use {@link #composite(List)} to combine the active beans into one.
 *
 * <p>Implementations MUST NOT throw — the caller invokes {@link #record(LlmCallData)} on the LLM
 * hot path and swallows failures, but sinks should still fail closed rather than leak exceptions.
 */
public interface LlmTelemetry {

    void record(LlmCallData data);

    /** Does nothing; the default when no telemetry sink is active (both flags off). */
    LlmTelemetry NOOP = data -> { };

    /**
     * Folds the active sinks into a single one: {@link #NOOP} if none, the sole element if one,
     * otherwise a fan-out that forwards to each in order. {@code NOOP} entries are filtered out.
     */
    static LlmTelemetry composite(List<LlmTelemetry> sinks) {
        List<LlmTelemetry> active = sinks.stream()
                .filter(s -> s != null && s != NOOP)
                .toList();
        return switch (active.size()) {
            case 0 -> NOOP;
            case 1 -> active.get(0);
            default -> data -> active.forEach(s -> s.record(data));
        };
    }
}
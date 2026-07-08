package dev.izquierdo.billmind._shared.infrastructure.llm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Publishes per-call LLM telemetry as Micrometer meters, so latency, token throughput and cost
 * surface on the Actuator {@code /metrics} and {@code /prometheus} endpoints alongside the
 * existing {@code pii.llm.*} and {@code invoice.*} instruments. Active by default; disable with
 * {@code billmind.observability.llm.metrics.enabled=false}.
 *
 * <p>Meters emitted (tagged {@code role}, {@code provider}, {@code model}):
 * <ul>
 *   <li>{@code llm.call.duration} — Timer, additionally tagged {@code operation}, {@code outcome}.</li>
 *   <li>{@code llm.calls} — Counter, additionally tagged {@code operation}, {@code outcome}.</li>
 *   <li>{@code llm.tokens} — Counter (by token count), tagged {@code direction=input|output}.</li>
 *   <li>{@code llm.cost.usd} — Counter (by USD), only when the model is priced.</li>
 * </ul>
 * Token counts and cost stay off the metric tag set to keep cardinality bounded.
 */
@Component
@ConditionalOnProperty(
        name = "billmind.observability.llm.metrics.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MetricsLlmTelemetry implements LlmTelemetry {

    private final MeterRegistry registry;

    public MetricsLlmTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(LlmCallData data) {
        Tags base = Tags.of(
                "role", data.role(),
                "provider", data.provider(),
                "model", data.model());
        Tags callTags = base.and("operation", data.operation(), "outcome", data.outcome());

        Timer.builder("llm.call.duration")
                .description("LLM call latency")
                .tags(callTags)
                .register(registry)
                .record(data.latencyMs(), TimeUnit.MILLISECONDS);

        registry.counter("llm.calls", callTags).increment();

        if (data.tokensIn() != null) {
            registry.counter("llm.tokens", base.and("direction", "input")).increment(data.tokensIn());
        }
        if (data.tokensOut() != null) {
            registry.counter("llm.tokens", base.and("direction", "output")).increment(data.tokensOut());
        }
        if (data.costUsd() != null) {
            registry.counter("llm.cost.usd", base).increment(data.costUsd());
        }
    }
}
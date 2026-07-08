package dev.izquierdo.billmind._shared.infrastructure.llm;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.time.Instant;

/**
 * Emits one OpenTelemetry span per LLM call, exported over OTLP to Langfuse (see
 * {@code LlmTracingConfig}). Wired only when {@code billmind.observability.llm.tracing.enabled=true}.
 *
 * <p>Because {@link #record(LlmCallData)} runs after the call has completed, the span is created
 * with an explicit start timestamp and closed immediately with the matching end timestamp, so its
 * duration still reflects the real call latency. Attributes follow the OpenTelemetry GenAI
 * semantic conventions ({@code gen_ai.*}) that Langfuse understands, plus a couple of BillMind
 * extras ({@code llm.role}, {@code llm.operation}, {@code gen_ai.usage.cost}).
 */
public class TracingLlmTelemetry implements LlmTelemetry {

    private final Tracer tracer;

    public TracingLlmTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void record(LlmCallData data) {
        Instant start = data.startedAt() != null
                ? data.startedAt()
                : Instant.now().minusMillis(data.latencyMs());

        SpanBuilder builder = tracer.spanBuilder("gen_ai " + data.operation())
                .setSpanKind(SpanKind.CLIENT)
                .setStartTimestamp(start)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.system", data.provider())
                .setAttribute("gen_ai.request.model", data.model())
                .setAttribute("llm.role", data.role())
                .setAttribute("llm.operation", data.operation());

        if (data.type() != null) {
            builder.setAttribute("llm.type", data.type());
        }
        if (data.tokensIn() != null) {
            builder.setAttribute("gen_ai.usage.input_tokens", data.tokensIn().longValue());
        }
        if (data.tokensOut() != null) {
            builder.setAttribute("gen_ai.usage.output_tokens", data.tokensOut().longValue());
        }
        if (data.tokensTotal() != null) {
            builder.setAttribute("gen_ai.usage.total_tokens", data.tokensTotal().longValue());
        }
        if (data.costUsd() != null) {
            builder.setAttribute("gen_ai.usage.cost", data.costUsd());
        }

        Span span = builder.startSpan();
        if (data.isError()) {
            span.setStatus(StatusCode.ERROR, data.error());
            span.setAttribute("error.type", data.error());
        }
        span.end(start.plusMillis(data.latencyMs()));
    }
}
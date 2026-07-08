package dev.izquierdo.billmind._shared.infrastructure.llm;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TracingLlmTelemetryTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;
    private TracingLlmTelemetry telemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = provider.get("test");
        telemetry = new TracingLlmTelemetry(tracer);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Test
    void shouldEmitSpanWithGenAiAttributes() {
        telemetry.record(new LlmCallData("LlmInvoiceFieldExtractor.extract", "extraction", "smart",
                "openai", "gpt-4o", Instant.now(), 1342L, 500, 100, 600, 0.0035, null));

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo("gen_ai LlmInvoiceFieldExtractor.extract");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.system"))).isEqualTo("openai");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model"))).isEqualTo("gpt-4o");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("llm.role"))).isEqualTo("smart");
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens"))).isEqualTo(500L);
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens"))).isEqualTo(100L);
        assertThat(span.getAttributes().get(AttributeKey.doubleKey("gen_ai.usage.cost"))).isEqualTo(0.0035);
    }

    @Test
    void shouldMarkSpanAsErrorOnFailure() {
        telemetry.record(new LlmCallData("op", null, "smart", "openai", "gpt-4o",
                Instant.now(), 50L, null, null, null, null, "SocketTimeoutException"));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("error.type")))
                .isEqualTo("SocketTimeoutException");
    }

    @Test
    void shouldReflectCallLatencyInSpanDuration() {
        telemetry.record(new LlmCallData("op", null, "fast", "openai", "gpt-4o",
                Instant.now(), 1000L, 10, 5, 15, null, null));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        long durationMs = (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000;
        assertThat(durationMs).isEqualTo(1000L);
    }

    @Test
    void shouldOmitTokenAttributesWhenAbsent() {
        telemetry.record(new LlmCallData("op", null, "fast", "ollama", "llama3.2",
                Instant.now(), 20L, null, null, null, null, null));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens"))).isNull();
        assertThat(span.getAttributes().get(AttributeKey.doubleKey("gen_ai.usage.cost"))).isNull();
    }
}
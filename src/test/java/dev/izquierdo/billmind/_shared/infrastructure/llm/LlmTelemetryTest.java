package dev.izquierdo.billmind._shared.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTelemetryTest {

    private static LlmCallData anyCall() {
        return new LlmCallData("op", null, "fast", "openai", "gpt-4o",
                Instant.now(), 10L, 1, 1, 2, null, null);
    }

    @Test
    void compositeOfNoneIsNoop() {
        assertThat(LlmTelemetry.composite(List.of())).isSameAs(LlmTelemetry.NOOP);
    }

    @Test
    void compositeFiltersOutNoopEntries() {
        LlmTelemetry real = data -> { };
        assertThat(LlmTelemetry.composite(List.of(LlmTelemetry.NOOP, real))).isSameAs(real);
    }

    @Test
    void compositeFansOutToEverySink() {
        List<String> hits = new ArrayList<>();
        LlmTelemetry a = data -> hits.add("a");
        LlmTelemetry b = data -> hits.add("b");

        LlmTelemetry.composite(List.of(a, b)).record(anyCall());

        assertThat(hits).containsExactly("a", "b");
    }
}
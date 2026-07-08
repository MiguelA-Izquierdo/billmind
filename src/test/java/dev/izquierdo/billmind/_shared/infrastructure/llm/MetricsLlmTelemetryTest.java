package dev.izquierdo.billmind._shared.infrastructure.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsLlmTelemetryTest {

    private SimpleMeterRegistry registry;
    private MetricsLlmTelemetry telemetry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        telemetry = new MetricsLlmTelemetry(registry);
    }

    private LlmCallData success() {
        return new LlmCallData("LlmInvoiceFieldExtractor.extract", "extraction", "smart",
                "openai", "gpt-4o", Instant.now(), 1342L, 500, 100, 600, 0.0035, null);
    }

    @Test
    void shouldRecordDurationTimerTaggedWithRouting() {
        telemetry.record(success());

        Timer timer = registry.find("llm.call.duration")
                .tag("provider", "openai").tag("model", "gpt-4o")
                .tag("role", "smart").tag("operation", "LlmInvoiceFieldExtractor.extract")
                .tag("outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isCloseTo(1342.0, within(1.0));
    }

    @Test
    void shouldCountCallWithOutcomeTag() {
        telemetry.record(success());

        Counter calls = registry.find("llm.calls").tag("outcome", "success").counter();
        assertThat(calls).isNotNull();
        assertThat(calls.count()).isEqualTo(1.0);
    }

    @Test
    void shouldSplitTokensByDirection() {
        telemetry.record(success());

        assertThat(registry.find("llm.tokens").tag("direction", "input").counter().count())
                .isEqualTo(500.0);
        assertThat(registry.find("llm.tokens").tag("direction", "output").counter().count())
                .isEqualTo(100.0);
    }

    @Test
    void shouldAccumulateCostWhenPresent() {
        telemetry.record(success());

        Counter cost = registry.find("llm.cost.usd").tag("model", "gpt-4o").counter();
        assertThat(cost).isNotNull();
        assertThat(cost.count()).isCloseTo(0.0035, within(1e-9));
    }

    @Test
    void shouldNotEmitCostWhenModelUnpriced() {
        telemetry.record(new LlmCallData("op", null, "fast", "ollama", "llama3.2",
                Instant.now(), 10L, 10, 5, 15, null, null));

        assertThat(registry.find("llm.cost.usd").counter()).isNull();
    }

    @Test
    void shouldTagOutcomeErrorAndSkipTokensOnFailure() {
        telemetry.record(new LlmCallData("op", null, "smart", "openai", "gpt-4o",
                Instant.now(), 50L, null, null, null, null, "SocketTimeoutException"));

        assertThat(registry.find("llm.calls").tag("outcome", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("llm.tokens").counters()).isEmpty();
    }
}
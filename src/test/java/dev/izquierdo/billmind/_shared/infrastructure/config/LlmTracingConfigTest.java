package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.infrastructure.llm.LlmTelemetry;
import dev.izquierdo.billmind._shared.infrastructure.llm.TracingLlmTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTracingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LlmTracingConfig.class);

    @Test
    void doesNotWireAnyTracingBeanWhenFlagOff() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OpenTelemetrySdk.class);
            assertThat(context).doesNotHaveBean(Tracer.class);
            assertThat(context).doesNotHaveBean(LlmTelemetry.class);
        });
    }

    @Test
    void wiresOtlpTracerAndSinkWhenEnabledWithHost() {
        runner.withPropertyValues(
                        "billmind.observability.llm.tracing.enabled=true",
                        "billmind.observability.llm.tracing.langfuse-host=http://langfuse.internal:3000",
                        "billmind.observability.llm.tracing.public-key=pk",
                        "billmind.observability.llm.tracing.secret-key=sk")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetrySdk.class);
                    assertThat(context).hasSingleBean(Tracer.class);
                    assertThat(context).getBean(LlmTelemetry.class)
                            .isInstanceOf(TracingLlmTelemetry.class);
                });
    }

    @Test
    void failsFastWhenEnabledWithoutHost() {
        runner.withPropertyValues("billmind.observability.llm.tracing.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
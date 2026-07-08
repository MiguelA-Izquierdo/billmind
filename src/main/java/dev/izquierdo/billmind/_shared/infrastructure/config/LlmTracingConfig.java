package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.infrastructure.llm.LlmTelemetry;
import dev.izquierdo.billmind._shared.infrastructure.llm.TracingLlmTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Builds the OpenTelemetry pipeline that ships LLM spans to a Langfuse backend over OTLP/HTTP.
 * Active only when {@code billmind.observability.llm.tracing.enabled=true}; when off, no OTel SDK
 * is created and {@link dev.izquierdo.billmind._shared.infrastructure.llm.TimedChatLanguageModel}
 * falls back to metrics-only (or no-op) telemetry.
 *
 * <p>Langfuse exposes a native OTLP trace endpoint at {@code {host}/api/public/otel/v1/traces},
 * authenticated with HTTP Basic using the project's public/secret keys. The backend itself is
 * external shared infrastructure (its own namespace, Postgres/ClickHouse); BillMind only references
 * it by {@code LANGFUSE_HOST} and injects the keys as secrets.
 */
@Configuration
@ConditionalOnProperty(name = "billmind.observability.llm.tracing.enabled", havingValue = "true")
public class LlmTracingConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmTracingConfig.class);

    private static final String OTLP_TRACES_PATH = "/api/public/otel/v1/traces";
    private static final String SERVICE_NAME = "billmind";

    private final String host;
    private final String publicKey;
    private final String secretKey;

    public LlmTracingConfig(
            @Value("${billmind.observability.llm.tracing.langfuse-host:}") String host,
            @Value("${billmind.observability.llm.tracing.public-key:}") String publicKey,
            @Value("${billmind.observability.llm.tracing.secret-key:}") String secretKey) {
        this.host = host;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    @Bean(destroyMethod = "close")
    public OpenTelemetrySdk llmOpenTelemetry() {
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException(
                    "LLM tracing is enabled but LANGFUSE_HOST is blank — set the Langfuse host or "
                    + "disable billmind.observability.llm.tracing.enabled");
        }
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(host.replaceAll("/+$", "") + OTLP_TRACES_PATH)
                .addHeader("Authorization", basicAuthHeader())
                .build();

        Resource resource = Resource.getDefault().merge(Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), SERVICE_NAME)));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(resource)
                .build();

        log.info("[LLM][TRACING] OTLP exporter wired to Langfuse at {}{}", host, OTLP_TRACES_PATH);
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }

    @Bean
    public Tracer llmTracer(OpenTelemetrySdk openTelemetry) {
        return openTelemetry.getTracer("billmind.llm");
    }

    @Bean
    public LlmTelemetry tracingLlmTelemetry(Tracer llmTracer) {
        return new TracingLlmTelemetry(llmTracer);
    }

    private String basicAuthHeader() {
        String credentials = publicKey + ":" + secretKey;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
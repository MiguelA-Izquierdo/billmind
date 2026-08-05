package dev.izquierdo.billmind._shared.infrastructure.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class TimedChatLanguageModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(TimedChatLanguageModel.class);

    private static final String BASE_PACKAGE = "dev.izquierdo.billmind";

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    public static final String MDC_OPERATION = "llm.operation";
    public static final String MDC_TYPE      = "llm.type";

    private final ChatModel delegate;
    private final String role;
    private final String provider;
    private final String model;
    private final LlmTelemetry telemetry;

    public TimedChatLanguageModel(ChatModel delegate, String role, String provider, String model) {
        this(delegate, role, provider, model, LlmTelemetry.NOOP);
    }

    public TimedChatLanguageModel(ChatModel delegate, String role, String provider, String model,
                                  LlmTelemetry telemetry) {
        this.delegate   = delegate;
        this.role       = role;
        this.provider   = provider;
        this.model      = model;
        this.telemetry  = telemetry != null ? telemetry : LlmTelemetry.NOOP;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String  operation = resolveOperation();
        String  type      = MDC.get(MDC_TYPE);
        Instant startedAt = Instant.now();
        long    start     = System.nanoTime();
        log.debug("[LLM][REQUEST] operation={}  role={}  provider={}  model={}  messages={}{}",
                operation, role, provider, model, request.messages().size(), formatMessages(request));
        try {
            ChatResponse response = delegate.chat(request);
            long latencyMs = elapsedMs(start);
            Double cost    = costUsd(response.tokenUsage());
            logCall(operation, type, latencyMs, response.tokenUsage(), cost, null);
            emit(operation, type, startedAt, latencyMs, response.tokenUsage(), cost, null);
            return response;
        } catch (Exception e) {
            long latencyMs = elapsedMs(start);
            String error   = e.getClass().getSimpleName();
            logCall(operation, type, latencyMs, null, null, error);
            emit(operation, type, startedAt, latencyMs, null, null, error);
            // Telemetry keeps the provider's own class name; callers get ours. Classifying here —
            // the one point every LLM call crosses — is what keeps a 429 from reaching the user as
            // a generic 500, without each adapter learning a provider's exception vocabulary.
            throw LlmFailures.translate(e);
        }
    }

    /**
     * Hands the call off to the telemetry sink (metrics/tracing). Never lets a telemetry failure
     * escape onto the LLM path — a broken exporter must not break inference.
     */
    private void emit(String operation, String type, Instant startedAt, long latencyMs,
                      TokenUsage tokens, Double cost, String error) {
        if (telemetry == LlmTelemetry.NOOP) return;
        try {
            telemetry.record(new LlmCallData(operation, type, role, provider, model,
                    startedAt, latencyMs,
                    tokens != null ? tokens.inputTokenCount()  : null,
                    tokens != null ? tokens.outputTokenCount() : null,
                    tokens != null ? tokens.totalTokenCount()  : null,
                    cost, error));
        } catch (Exception e) {
            log.warn("[LLM][TELEMETRY] sink failed ({}); ignoring", e.getClass().getSimpleName());
        }
    }

    /** USD cost for the call, or null when the model is unpriced or usage is absent. */
    private Double costUsd(TokenUsage tokens) {
        if (tokens == null) return null;
        Optional<ModelPricingRegistry.Pricing> pricing = ModelPricingRegistry.lookup(model);
        if (pricing.isEmpty()) return null;
        int inTokens  = tokens.inputTokenCount()  != null ? tokens.inputTokenCount()  : 0;
        int outTokens = tokens.outputTokenCount() != null ? tokens.outputTokenCount() : 0;
        return pricing.get().cost(inTokens, outTokens);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return delegate.doChat(request);
    }

    /**
     * MDC wins if set explicitly by the caller; otherwise the caller class+method is derived
     * from the stack so callers never need to manage MDC themselves. Uses {@link StackWalker},
     * which lazily evaluates frames and short-circuits at the first match, avoiding the cost of
     * materializing the entire stack trace ({@code Thread.getStackTrace()}) on every LLM call.
     */
    private static String resolveOperation() {
        String explicit = MDC.get(MDC_OPERATION);
        if (explicit != null) return explicit;

        return STACK_WALKER.walk(frames -> frames
                .filter(f -> f.getClassName().startsWith(BASE_PACKAGE)
                        && !f.getClassName().contains("TimedChatLanguageModel"))
                .map(f -> simpleClassName(f.getClassName()) + "." + f.getMethodName())
                .findFirst()
                .orElse("unknown"));
    }

    private static String simpleClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private void logCall(String operation, String type, long latencyMs, TokenUsage tokens,
                         Double cost, String error) {
        StringBuilder sb = new StringBuilder("[LLM]");
        sb.append("  operation=").append(operation);
        if (type != null) sb.append("  type=").append(type);
        sb.append("  role=").append(role);
        sb.append("  provider=").append(provider);
        sb.append("  model=").append(model);
        sb.append("  latency=").append(latencyMs).append("ms");
        if (tokens != null) {
            sb.append("  tokensIn=").append(tokens.inputTokenCount());
            sb.append("  tokensOut=").append(tokens.outputTokenCount());
            sb.append("  tokensTotal=").append(tokens.totalTokenCount());
            if (cost != null) {
                sb.append(String.format(Locale.US, "  costUsd=%.6f", cost));
            }
        }
        if (error != null) sb.append("  error=").append(error);
        log.info("{}", sb);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private static String formatMessages(ChatRequest request) {
        return request.messages().stream()
                .map(m -> "\n  [" + m.type().name() + "] " + extractText(m))
                .collect(Collectors.joining());
    }

    private static String extractText(ChatMessage msg) {
        if (msg instanceof SystemMessage m) return m.text();
        if (msg instanceof AiMessage    m) return m.text() != null ? m.text() : "[tool calls]";
        if (msg instanceof UserMessage  m) return m.contents().stream()
                .map(c -> c instanceof TextContent tc ? tc.text() : c.toString())
                .collect(Collectors.joining("\n"));
        return msg.toString();
    }
}
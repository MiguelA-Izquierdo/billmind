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

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class TimedChatLanguageModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(TimedChatLanguageModel.class);

    private static final String BASE_PACKAGE = "dev.izquierdo.billmind";

    public static final String MDC_OPERATION = "llm.operation";
    public static final String MDC_TYPE      = "llm.type";

    private final ChatModel delegate;
    private final String role;
    private final String provider;
    private final String model;

    public TimedChatLanguageModel(ChatModel delegate, String role, String provider, String model) {
        this.delegate = delegate;
        this.role     = role;
        this.provider = provider;
        this.model    = model;
    }
    
    @Override
    public ChatResponse chat(ChatRequest request) {
        String operation = resolveOperation();
        String type      = MDC.get(MDC_TYPE);
        long   start     = System.nanoTime();
        log.debug("[LLM][REQUEST] operation={}  role={}  provider={}  model={}  messages={}{}",
                operation, role, provider, model, request.messages().size(), formatMessages(request));
        try {
            ChatResponse response = delegate.chat(request);
            logCall(operation, type, elapsedMs(start), response.tokenUsage(), null);
            return response;
        } catch (Exception e) {
            logCall(operation, type, elapsedMs(start), null, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return delegate.doChat(request);
    }

    /**
     * MDC wins if set explicitly by the caller; otherwise the caller class+method is derived
     * from the stack trace so callers never need to manage MDC themselves.
     */
    private static String resolveOperation() {
        String explicit = MDC.get(MDC_OPERATION);
        if (explicit != null) return explicit;

        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith(BASE_PACKAGE) && !cls.contains("TimedChatLanguageModel")) {
                return simpleClassName(cls) + "." + frame.getMethodName();
            }
        }
        return "unknown";
    }

    private static String simpleClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private void logCall(String operation, String type, long latencyMs, TokenUsage tokens, String error) {
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
            Optional<ModelPricingRegistry.Pricing> pricing = ModelPricingRegistry.lookup(model);
            if (pricing.isPresent()) {
                int inTokens  = tokens.inputTokenCount()  != null ? tokens.inputTokenCount()  : 0;
                int outTokens = tokens.outputTokenCount() != null ? tokens.outputTokenCount() : 0;
                double cost = pricing.get().cost(inTokens, outTokens);
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
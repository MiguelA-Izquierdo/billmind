package dev.izquierdo.billmind.assistant.infrastructure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmRateLimitedException;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.ThrottleMessages;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Wraps a chat turn in an SSE stream. The HTTP status is committed the moment the stream opens, so
 * a failure can only be reported <em>inside</em> it: every error leaves as an {@code error} event
 * carrying a {@code code} the UI can branch on, and the stream is then closed cleanly. Closing with
 * {@code completeWithError} instead would surface in the browser as a network failure and hide the
 * message we just sent.
 */
@Component
public class ChatSsePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatSsePublisher.class);
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private static final String GENERIC_MESSAGE =
            "No he podido procesar tu pregunta. Vuelve a intentarlo en unos instantes.";

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatSsePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter publishError(String errorMessage) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sendFailure(emitter, "BAD_REQUEST", errorMessage, Duration.ZERO);
        return emitter;
    }

    public SseEmitter publish(Supplier<ChatResult> chatSupplier) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                ChatResult result = chatSupplier.get();
                send(emitter, Map.of("type", "conversation", "id", result.conversationId().toString()));
                // One event carries the whole answer — this is an SSE transport envelope, not token streaming.
                send(emitter, Map.of("type", "message", "content", result.answer()));
                send(emitter, Map.of("type", "citations", "items",
                        result.citations().stream()
                                .map(c -> Map.of("title", c.title(), "source", c.source(), "docType", c.docType()))
                                .toList()));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (LlmRateLimitedException e) {
                log.warn("[CHAT] model provider throttled us (retryAfter={}s)", e.retryAfter().toSeconds());
                sendFailure(emitter, "RATE_LIMITED",
                        ThrottleMessages.llmThrottled(e.getMessage(), e.retryAfter()), e.retryAfter());
            } catch (LlmServiceUnavailableException e) {
                log.error("[CHAT] model provider unavailable: {}", e.getMessage(), e);
                sendFailure(emitter, "LLM_UNAVAILABLE", e.getMessage(), Duration.ZERO);
            } catch (Exception e) {
                log.error("Error in assistant chat: {}", e.getMessage(), e);
                sendFailure(emitter, "INTERNAL", GENERIC_MESSAGE, Duration.ZERO);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter, Object payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    /** {@code retryAfter} of {@code 0} means "unknown", never "retry now". */
    private void sendFailure(SseEmitter emitter, String code, String message, Duration retryAfter) {
        try {
            send(emitter, Map.of(
                    "type", "error",
                    "code", code,
                    "content", message,
                    "retryAfter", retryAfter.toSeconds()));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception ignored) {
            // The client is already gone — there is nobody left to tell.
        }
    }
}
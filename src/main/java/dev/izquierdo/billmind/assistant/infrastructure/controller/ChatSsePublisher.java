package dev.izquierdo.billmind.assistant.infrastructure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Component
public class ChatSsePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatSsePublisher.class);
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatSsePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter publishError(String errorMessage) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            send(emitter, Map.of("type", "error", "content", errorMessage));
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception ignored) {}
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
            } catch (Exception e) {
                log.error("Error in assistant chat: {}", e.getMessage(), e);
                trySendError(emitter);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter, Object payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void trySendError(SseEmitter emitter) {
        try {
            send(emitter, Map.of("type", "error", "content", "Ha ocurrido un error al procesar tu pregunta."));
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (Exception ignored) {}
    }
}
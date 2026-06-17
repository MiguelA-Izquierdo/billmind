package dev.izquierdo.billmind.assistant.infrastructure.controller;

import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.application.usecase.ChatUseCase;
import dev.izquierdo.billmind.assistant.infrastructure.controller.dto.ChatRequest;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final ChatUseCase chatUseCase;
    private final SessionContext sessionContext;
    private final ChatSsePublisher ssePublisher;

    public AssistantController(ChatUseCase chatUseCase,
                                SessionContext sessionContext,
                                ChatSsePublisher ssePublisher) {
        this.chatUseCase    = chatUseCase;
        this.sessionContext = sessionContext;
        this.ssePublisher   = ssePublisher;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        ChatCommand command;
        try {
            command = new ChatCommand(
                    sessionContext.getSessionId(),
                    request.invoiceId(),
                    request.conversationId(),
                    request.message());
        } catch (IllegalArgumentException e) {
            return ssePublisher.publishError(e.getMessage());
        }
        return ssePublisher.publish(() -> chatUseCase.execute(command));
    }
}
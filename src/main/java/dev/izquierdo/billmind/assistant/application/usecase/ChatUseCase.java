package dev.izquierdo.billmind.assistant.application.usecase;

import dev.izquierdo.billmind._shared.domain.event.DomainEventPublisher;
import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.application.service.ChatContextAssembler;
import dev.izquierdo.billmind.assistant.application.service.ConversationService;
import dev.izquierdo.billmind.assistant.domain.event.AssistantQuestionAnswered;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ChatUseCase {

    private final ConversationService conversationService;
    private final ChatContextAssembler contextAssembler;
    private final AssistantLlmPort llmPort;
    private final DomainEventPublisher eventPublisher;

    public ChatUseCase(
            ConversationService conversationService,
            ChatContextAssembler contextAssembler,
            AssistantLlmPort llmPort,
            DomainEventPublisher eventPublisher) {
        this.conversationService = Objects.requireNonNull(conversationService);
        this.contextAssembler    = Objects.requireNonNull(contextAssembler);
        this.llmPort             = Objects.requireNonNull(llmPort);
        this.eventPublisher      = Objects.requireNonNull(eventPublisher);
    }

    public ChatResult execute(ChatCommand command) {
        Conversation conversation = conversationService.resolve(command);
        ChatContext context = contextAssembler.assemble(command.invoiceId(), command.sessionId(), command.message());
        ChatResult result = llmPort.answer(context, command.message(), conversation.getRecentMessages(6));
        conversationService.recordExchange(conversation, command.message(), result.answer());
        publishAnswered(command, conversation, result);
        return new ChatResult(conversation.getId(), result.answer(), result.citations());
    }

    private void publishAnswered(ChatCommand command, Conversation conversation, ChatResult result) {
        int citationCount = result.citations() == null ? 0 : result.citations().size();
        eventPublisher.publish(AssistantQuestionAnswered.of(
                conversation.getId(),
                command.sessionId(),
                command.invoiceId(),
                command.message().length(),
                citationCount));
    }
}
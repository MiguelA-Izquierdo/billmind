package dev.izquierdo.billmind.assistant.application.usecase;

import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.application.service.ChatContextAssembler;
import dev.izquierdo.billmind.assistant.application.service.ConversationService;
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

    public ChatUseCase(
            ConversationService conversationService,
            ChatContextAssembler contextAssembler,
            AssistantLlmPort llmPort) {
        this.conversationService = Objects.requireNonNull(conversationService);
        this.contextAssembler    = Objects.requireNonNull(contextAssembler);
        this.llmPort             = Objects.requireNonNull(llmPort);
    }

    public ChatResult execute(ChatCommand command) {
        Conversation conversation = conversationService.resolve(command);
        ChatContext context = contextAssembler.assemble(command.invoiceId(), command.message());
        ChatResult result = llmPort.answer(context, command.message(), conversation.getRecentMessages(6));
        conversationService.recordExchange(conversation, command.message(), result.answer());
        return new ChatResult(conversation.getId(), result.answer(), result.citations());
    }
}
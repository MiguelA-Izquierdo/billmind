package dev.izquierdo.billmind.assistant.application.service;

import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ConversationService {

    private final AssistantRepository repository;

    public ConversationService(AssistantRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Conversation resolve(ChatCommand command) {
        if (command.conversationId() == null) {
            return Conversation.create(command.sessionId(), command.invoiceId());
        }
        return repository.findById(command.conversationId())
                .orElseGet(() -> Conversation.create(command.sessionId(), command.invoiceId()));
    }

    public void recordExchange(Conversation conversation, String userMessage, String assistantAnswer) {
        conversation.addMessage(ConversationMessage.create(MessageRole.USER, userMessage));
        conversation.addMessage(ConversationMessage.create(MessageRole.ASSISTANT, assistantAnswer));
        repository.save(conversation);
    }
}
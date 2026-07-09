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

    /**
     * Continues the referenced conversation only when it belongs to the calling session. A
     * conversation owned by another session is treated as if it did not exist, so a guessed
     * {@code conversationId} cannot pull a stranger's history into the prompt.
     */
    public Conversation resolve(ChatCommand command) {
        if (command.conversationId() == null) {
            return Conversation.create(command.sessionId(), command.invoiceId());
        }
        return repository.findById(command.conversationId())
                .filter(conversation -> command.sessionId().equals(conversation.getSessionId()))
                .orElseGet(() -> Conversation.create(command.sessionId(), command.invoiceId()));
    }

    public void recordExchange(Conversation conversation, String userMessage, String assistantAnswer) {
        conversation.addMessage(ConversationMessage.create(MessageRole.USER, userMessage));
        conversation.addMessage(ConversationMessage.create(MessageRole.ASSISTANT, assistantAnswer));
        repository.save(conversation);
    }
}
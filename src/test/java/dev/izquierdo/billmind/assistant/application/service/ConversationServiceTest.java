package dev.izquierdo.billmind.assistant.application.service;

import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private AssistantRepository repository;

    @InjectMocks
    private ConversationService conversationService;

    private static final UUID SESSION_ID      = UUID.randomUUID();
    private static final UUID INVOICE_ID      = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    // --- resolve ---

    @Test
    void shouldCreateNewConversationWhenConversationIdIsNull() {
        ChatCommand command = new ChatCommand(SESSION_ID, INVOICE_ID, null, "hola");

        Conversation result = conversationService.resolve(command);

        assertThat(result.getId()).isNotNull();
        verify(repository, never()).findById(any());
    }

    @Test
    void shouldReturnExistingConversationWhenFoundById() {
        Conversation existing = Conversation.create(SESSION_ID, INVOICE_ID);
        ChatCommand command = new ChatCommand(SESSION_ID, INVOICE_ID, CONVERSATION_ID, "hola");
        when(repository.findById(CONVERSATION_ID)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.resolve(command);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void shouldCreateNewConversationWhenConversationIdNotFound() {
        ChatCommand command = new ChatCommand(SESSION_ID, INVOICE_ID, CONVERSATION_ID, "hola");
        when(repository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        Conversation result = conversationService.resolve(command);

        assertThat(result.getId()).isNotEqualTo(CONVERSATION_ID);
    }

    @Test
    void shouldCreateConversationWithSessionAndInvoiceIdFromCommand() {
        ChatCommand command = new ChatCommand(SESSION_ID, INVOICE_ID, null, "hola");

        Conversation result = conversationService.resolve(command);

        assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(result.getInvoiceId()).isEqualTo(INVOICE_ID);
    }

    // --- recordExchange ---

    @Test
    void shouldAddUserAndAssistantMessagesInOrder() {
        Conversation conversation = Conversation.create(SESSION_ID, INVOICE_ID);

        conversationService.recordExchange(conversation, "pregunta", "respuesta");

        assertThat(conversation.getMessages()).hasSize(2);
        assertThat(conversation.getMessages().get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(conversation.getMessages().get(0).getContent()).isEqualTo("pregunta");
        assertThat(conversation.getMessages().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(conversation.getMessages().get(1).getContent()).isEqualTo("respuesta");
    }

    @Test
    void shouldSaveConversationAfterRecordingExchange() {
        Conversation conversation = Conversation.create(SESSION_ID, INVOICE_ID);

        conversationService.recordExchange(conversation, "pregunta", "respuesta");

        verify(repository).save(conversation);
    }

    @Test
    void shouldAppendToExistingMessages() {
        Conversation conversation = Conversation.create(SESSION_ID, INVOICE_ID);
        conversationService.recordExchange(conversation, "primera", "respuesta1");

        conversationService.recordExchange(conversation, "segunda", "respuesta2");

        assertThat(conversation.getMessages()).hasSize(4);
        assertThat(conversation.getMessages().get(2).getContent()).isEqualTo("segunda");
        assertThat(conversation.getMessages().get(3).getContent()).isEqualTo("respuesta2");
    }
}
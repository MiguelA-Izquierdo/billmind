package dev.izquierdo.billmind.assistant.application.usecase;

import dev.izquierdo.billmind._shared.domain.event.DomainEventPublisher;
import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.application.service.ChatContextAssembler;
import dev.izquierdo.billmind.assistant.application.service.ConversationService;
import dev.izquierdo.billmind.assistant.domain.event.AssistantQuestionAnswered;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUseCaseTest {

    @Mock private ConversationService  conversationService;
    @Mock private ChatContextAssembler contextAssembler;
    @Mock private AssistantLlmPort     llmPort;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private ChatUseCase chatUseCase;

    private static final UUID   SESSION_ID      = UUID.randomUUID();
    private static final UUID   INVOICE_ID      = UUID.randomUUID();
    private static final UUID   CONVERSATION_ID = UUID.randomUUID();
    private static final String MESSAGE         = "¿Cuánto pago de potencia?";

    private ChatCommand  command;
    private Conversation conversation;
    private ChatContext  context;
    private ChatResult   llmResult;

    @BeforeEach
    void setUp() {
        command      = new ChatCommand(SESSION_ID, INVOICE_ID, CONVERSATION_ID, MESSAGE);
        conversation = Conversation.create(SESSION_ID, INVOICE_ID);
        context      = new ChatContext(null, List.of(), List.of(), null);
        llmResult    = new ChatResult(null, "Pagas 12 € de potencia.", List.of());
    }

    @Test
    void shouldReturnAnswerFromLlm() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        ChatResult result = chatUseCase.execute(command);

        assertThat(result.answer()).isEqualTo("Pagas 12 € de potencia.");
    }

    @Test
    void shouldReturnConversationIdFromResolvedConversation() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        ChatResult result = chatUseCase.execute(command);

        assertThat(result.conversationId()).isEqualTo(conversation.getId());
    }

    @Test
    void shouldPropagateCitationsFromLlmResult() {
        ChatCitation citation = new ChatCitation("Guía 2.0TD", "REE", "GUIDE");
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any()))
                .thenReturn(new ChatResult(null, "respuesta", List.of(citation)));

        ChatResult result = chatUseCase.execute(command);

        assertThat(result.citations()).containsExactly(citation);
    }

    @Test
    void shouldDelegateContextAssemblyWithInvoiceIdAndMessage() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        chatUseCase.execute(command);

        verify(contextAssembler).assemble(INVOICE_ID, SESSION_ID, MESSAGE);
    }

    @Test
    void shouldPassAssembledContextAndRecentMessagesToLlm() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        chatUseCase.execute(command);

        verify(llmPort).answer(eq(context), eq(MESSAGE), eq(conversation.getRecentMessages(6)));
    }

    @Test
    void shouldRecordExchangeWithUserMessageAndLlmAnswer() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        chatUseCase.execute(command);

        verify(conversationService).recordExchange(conversation, MESSAGE, "Pagas 12 € de potencia.");
    }

    @Test
    void shouldWorkWithoutInvoiceId() {
        ChatCommand noInvoiceCommand = new ChatCommand(SESSION_ID, null, null, "¿Qué es el PVPC?");
        ChatContext emptyContext = new ChatContext(null, List.of(), List.of(), null);
        when(conversationService.resolve(noInvoiceCommand)).thenReturn(conversation);
        when(contextAssembler.assemble(null, SESSION_ID, "¿Qué es el PVPC?")).thenReturn(emptyContext);
        when(llmPort.answer(eq(emptyContext), eq("¿Qué es el PVPC?"), anyList())).thenReturn(llmResult);

        ChatResult result = chatUseCase.execute(noInvoiceCommand);

        assertThat(result.answer()).isEqualTo("Pagas 12 € de potencia.");
        verify(contextAssembler).assemble(null, SESSION_ID, "¿Qué es el PVPC?");
    }

    @Test
    void shouldWorkWithoutConversationId() {
        ChatCommand noConversationCommand = new ChatCommand(SESSION_ID, INVOICE_ID, null, MESSAGE);
        when(conversationService.resolve(noConversationCommand)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        ChatResult result = chatUseCase.execute(noConversationCommand);

        assertThat(result.conversationId()).isNotNull();
        verify(conversationService).resolve(noConversationCommand);
    }

    @Test
    void shouldPublishQuestionAnsweredWithCitationCount() {
        ChatCitation citation = new ChatCitation("Guía 2.0TD", "REE", "GUIDE");
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any()))
                .thenReturn(new ChatResult(null, "respuesta", List.of(citation)));

        chatUseCase.execute(command);

        ArgumentCaptor<AssistantQuestionAnswered> captor =
                ArgumentCaptor.forClass(AssistantQuestionAnswered.class);
        verify(eventPublisher).publish(captor.capture());
        AssistantQuestionAnswered.Payload payload = captor.getValue().getData();
        assertThat(payload.conversationId()).isEqualTo(conversation.getId());
        assertThat(payload.sessionId()).isEqualTo(SESSION_ID);
        assertThat(payload.invoiceId()).isEqualTo(INVOICE_ID);
        assertThat(payload.questionLength()).isEqualTo(MESSAGE.length());
        assertThat(payload.citationCount()).isEqualTo(1);
    }

    @Test
    void shouldPublishQuestionAnsweredWithZeroCitationsWhenNoneRetrieved() {
        when(conversationService.resolve(command)).thenReturn(conversation);
        when(contextAssembler.assemble(INVOICE_ID, SESSION_ID, MESSAGE)).thenReturn(context);
        when(llmPort.answer(any(), any(), any())).thenReturn(llmResult);

        chatUseCase.execute(command);

        ArgumentCaptor<AssistantQuestionAnswered> captor =
                ArgumentCaptor.forClass(AssistantQuestionAnswered.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getData().citationCount()).isZero();
    }
}
package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.cache.CaffeineToolResultCache;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.tool.AssistantTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgenticAssistantLlmAdapterTest {

    @Mock private ChatModel smartChatModel;
    @Mock private AssistantTools tools;

    private AgenticAssistantLlmAdapter adapter;

    private static final ElectricityFields FIELDS = new ElectricityFields(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), new BigDecimal("45.50"),
            null, null, null, null, null, null, null, null, null);

    private static final ChatContext CONTEXT = ChatContext.invoiceOnly(FIELDS);
    private static final String QUESTION = "¿Qué es el término de potencia?";

    @BeforeEach
    void setUp() {
        // Real Caffeine cache (fresh per test) — exercises the interface and keeps isolation.
        adapter = new AgenticAssistantLlmAdapter(
                smartChatModel, tools, new CaffeineToolResultCache(500, Duration.ofHours(2)));
    }

    private static ChatResponse response(AiMessage message) {
        return ChatResponse.builder().aiMessage(message).build();
    }

    private static AiMessage toolCall(String name, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1").name(name).arguments(arguments).build());
    }

    @Test
    void shouldExecuteToolThenReturnFinalTextAndAccumulatedCitations() {
        when(smartChatModel.chat(any(ChatRequest.class))).thenReturn(
                response(toolCall("search_regulation", "{\"query\":\"potencia\"}")),
                response(AiMessage.from("El término de potencia es el coste fijo por la potencia contratada.")));
        when(tools.dispatch(any(), any(), anyList())).thenAnswer(inv -> {
            List<RegulatorySnippet> sink = inv.getArgument(2);
            sink.add(new RegulatorySnippet("Guía 2.0TD", "REE", "GUIDE", "contenido"));
            return "resultado de la tool";
        });

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).contains("término de potencia");
        assertThat(result.citations())
                .containsExactly(new ChatResult.ChatCitation("Guía 2.0TD", "REE", "GUIDE"));
        verify(tools).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldReturnImmediatelyWhenModelRequestsNoTools() {
        when(smartChatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from("Tu factura es de electricidad por 45,50 €.")));

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).contains("45,50");
        assertThat(result.citations()).isEmpty();
        verify(tools, never()).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldDeduplicateCitationsWhenSameSnippetRetrievedTwice() {
        RegulatorySnippet snippet = new RegulatorySnippet("Guía 2.0TD", "REE", "GUIDE", "contenido");
        when(smartChatModel.chat(any(ChatRequest.class))).thenReturn(
                response(toolCall("search_regulation", "{\"query\":\"a\"}")),
                response(toolCall("search_regulation", "{\"query\":\"b\"}")),
                response(AiMessage.from("Respuesta final.")));
        when(tools.dispatch(any(), any(), anyList())).thenAnswer(inv -> {
            ((List<RegulatorySnippet>) inv.getArgument(2)).add(snippet);
            return "resultado";
        });

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.citations())
                .containsExactly(new ChatResult.ChatCitation("Guía 2.0TD", "REE", "GUIDE"));
        verify(tools, times(2)).dispatch(any(), any(), anyList());
    }

    @Test
    void shouldForceFinalAnswerWithoutToolsWhenRoundsExhausted() {
        // Distinct queries each round so the short-circuit never triggers and all 5 rounds run.
        when(smartChatModel.chat(any(ChatRequest.class))).thenReturn(
                response(toolCall("search_regulation", "{\"query\":\"x1\"}")),  // round 1
                response(toolCall("search_regulation", "{\"query\":\"x2\"}")),  // round 2
                response(toolCall("search_regulation", "{\"query\":\"x3\"}")),  // round 3
                response(toolCall("search_regulation", "{\"query\":\"x4\"}")),  // round 4
                response(toolCall("search_regulation", "{\"query\":\"x5\"}")),  // round 5
                response(AiMessage.from("Respuesta forzada sin tools.")));      // final call
        when(tools.dispatch(any(), any(), anyList())).thenReturn("resultado");

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).isEqualTo("Respuesta forzada sin tools.");
        verify(tools, times(5)).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(6)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldShortCircuitWhenModelRepeatsAlreadyServedToolCall() {
        // The model asks for the same call twice; the second round short-circuits to a tool-less answer.
        when(smartChatModel.chat(any(ChatRequest.class))).thenReturn(
                response(toolCall("search_regulation", "{\"query\":\"x\"}")),   // round 1: executed
                response(toolCall("search_regulation", "{\"query\":\"x\"}")),   // round 2: repeat -> short-circuit
                response(AiMessage.from("Respuesta final sin repetir la tool.")));
        when(tools.dispatch(any(), any(), anyList())).thenReturn("resultado");

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).isEqualTo("Respuesta final sin repetir la tool.");
        verify(tools, times(1)).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(3)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldRecoverWithToollessAnswerWhenModelReturnsInvalidToolCall() {
        // Reproduces the Groq 400 tool_use_failed: the round-2 tool call blows up on the provider.
        when(smartChatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("search_regulation", "{\"query\":\"x\"}")))
                .thenThrow(new InvalidRequestException("400 tool_use_failed"))
                .thenReturn(response(AiMessage.from("Respuesta segura sin herramientas.")));
        when(tools.dispatch(any(), any(), anyList())).thenReturn("resultado");

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).isEqualTo("Respuesta segura sin herramientas.");
        verify(tools, times(1)).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(3)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldReturnFallbackMessageWhenModelFailsRepeatedly() {
        // Invalid tool call, then the tool-less retry also fails -> degraded Spanish fallback, no crash.
        when(smartChatModel.chat(any(ChatRequest.class)))
                .thenThrow(new InvalidRequestException("400 tool_use_failed"))
                .thenThrow(new RuntimeException("groq 500"));

        ChatResult result = adapter.answer(CONTEXT, QUESTION, List.of());

        assertThat(result.answer()).contains("No he podido completar");
        assertThat(result.citations()).isEmpty();
        verify(tools, never()).dispatch(any(), any(), anyList());
        verify(smartChatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void shouldMapConversationHistoryIntoMessages() {
        when(smartChatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from("Respuesta.")));
        List<ConversationMessage> history = List.of(
                ConversationMessage.create(MessageRole.USER, "Hola"),
                ConversationMessage.create(MessageRole.ASSISTANT, "¿En qué te ayudo?"));

        ChatResult result = adapter.answer(CONTEXT, QUESTION, history);

        assertThat(result.answer()).isEqualTo("Respuesta.");
        verify(smartChatModel, times(1)).chat(any(ChatRequest.class));
    }
}
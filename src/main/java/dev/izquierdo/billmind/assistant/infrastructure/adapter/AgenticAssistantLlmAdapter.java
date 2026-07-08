package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.tool.AssistantTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agentic assistant adapter: instead of eagerly loading every context source, it inlines only
 * the user's invoice and lets the LLM decide which retrieval tool to call (comparison, market
 * rates, regulatory search). Runs a manual low-level tool-calling loop so it stays compatible
 * with {@code TimedChatLanguageModel} instrumentation, the hexagonal ports and precise citation
 * tracking. Active only when {@code assistant.tools.enabled=true}; otherwise
 * {@link LlmAssistantAdapter} handles the turn.
 */
@Component
@ConditionalOnProperty(name = "assistant.tools.enabled", havingValue = "true")
public class AgenticAssistantLlmAdapter implements AssistantLlmPort {

    private static final Logger log = LoggerFactory.getLogger(AgenticAssistantLlmAdapter.class);

    private static final int MAX_OUTPUT_TOKENS = 400;
    private static final int MAX_TOOL_ROUNDS   = 5;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are BillMind, an expert assistant specialized in Spanish utility invoices \
            (electricity, gas, water, and telecom).
            Help users understand their invoices by answering questions clearly and accurately.

            Rules:
            1. The user's invoice data is provided below. For anything else you need — the market \
            rates, the price comparison, or regulatory information — call the appropriate tool. \
            Do not invent data.
            2. When the user asks whether they are paying too much or which tariff is cheaper, call \
            get_invoice_comparison and explain its precomputed result. Do NOT rank raw market rates yourself.
            3. For questions about a specific company or tariff, call search_market_rates.
            4. For questions about regulation, concepts or invoice terms, call search_regulation.
            5. When you reference regulatory information, cite the source document title returned by \
            the tool (e.g. "según la guía de la tarifa 2.0TD de REE").
            6. Only call a tool when it is actually needed to answer; simple questions about the \
            invoice data below need no tool.
            7. If the question cannot be answered from the invoice or the tools, say so clearly.
            8. Keep answers concise: maximum 3 short paragraphs. Use bullet points for lists.
            9. Never repeat the full invoice data back to the user.
            10. Respond in Spanish.

            --- DATOS DE LA FACTURA ---
            %s
            --- FIN FACTURA ---
            """;

    private final ChatModel smartChatModel;
    private final AssistantTools tools;

    public AgenticAssistantLlmAdapter(
            @Qualifier("smartChatModel") ChatModel smartChatModel,
            AssistantTools tools) {
        this.smartChatModel = smartChatModel;
        this.tools = tools;
    }

    @Override
    public ChatResult answer(ChatContext context, String question, List<ConversationMessage> history) {
        List<ChatMessage> messages = openingMessages(context, question, history);
        List<RegulatorySnippet> citationSink = new ArrayList<>();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            AiMessage ai = smartChatModel.chat(withTools(messages)).aiMessage();
            messages.add(ai);
            if (!ai.hasToolExecutionRequests()) {
                return finalResult(ai.text(), citationSink);
            }
            executeToolRequests(ai, context, messages, citationSink, round);
        }

        // Rounds exhausted: force a final textual answer without offering more tools.
        log.warn("[AGENT] tool rounds exhausted (max={}); forcing a final answer without tools",
                MAX_TOOL_ROUNDS);
        String answer = smartChatModel.chat(withoutTools(messages)).aiMessage().text();
        return finalResult(answer, citationSink);
    }

    private void executeToolRequests(AiMessage ai, ChatContext context, List<ChatMessage> messages,
                                     List<RegulatorySnippet> citationSink, int round) {
        log.info("[AGENT][round={}] tool calls requested: {}", round + 1,
                ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList());
        for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
            String result = tools.dispatch(req, context.invoiceFields(), citationSink);
            messages.add(ToolExecutionResultMessage.from(req, result));
        }
    }

    private ChatResult finalResult(String answer, List<RegulatorySnippet> citationSink) {
        List<ChatCitation> citations = toCitations(citationSink);
        log.info("[AGENT] answer produced ({} citations)", citations.size());
        log.debug("[AGENT] answer text: {}", answer);
        return new ChatResult(null, answer, citations);
    }

    private List<ChatMessage> openingMessages(
            ChatContext context, String question, List<ConversationMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemContent(context)));
        for (ConversationMessage msg : history) {
            messages.add(msg.getRole() == MessageRole.USER
                    ? UserMessage.from(msg.getContent())
                    : AiMessage.from(msg.getContent()));
        }
        messages.add(UserMessage.from(question));
        return messages;
    }

    private ChatRequest withTools(List<ChatMessage> messages) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .maxOutputTokens(MAX_OUTPUT_TOKENS)
                        .toolSpecifications(tools.specifications())
                        .build())
                .build();
    }

    private ChatRequest withoutTools(List<ChatMessage> messages) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .maxOutputTokens(MAX_OUTPUT_TOKENS)
                        .build())
                .build();
    }

    private String buildSystemContent(ChatContext context) {
        String invoice = context.invoiceFields() != null
                ? AssistantContextFormatter.formatFields(context.invoiceFields())
                : "No se ha proporcionado factura.";
        return SYSTEM_PROMPT_TEMPLATE.formatted(invoice);
    }

    private static List<ChatCitation> toCitations(List<RegulatorySnippet> snippets) {
        return snippets.stream()
                .map(r -> new ChatCitation(r.title(), r.source(), r.docType()))
                .distinct()
                .toList();
    }
}
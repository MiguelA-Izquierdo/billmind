package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.cache.CachedToolResult;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.cache.ToolResultCache;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.tool.AssistantTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Agentic assistant adapter: instead of eagerly loading every context source, it inlines only
 * the user's invoice and lets the LLM decide which retrieval tool to call (comparison, market
 * rates, regulatory search). Runs a manual low-level tool-calling loop so it stays compatible
 * with {@code TimedChatLanguageModel} instrumentation, the hexagonal ports and precise citation
 * tracking. Active only when {@code assistant.tools.enabled=true}; otherwise
 * {@link LlmAssistantAdapter} handles the turn.
 *
 * <p>Three safeguards keep a turn robust against unreliable tool calling (observed with Groq's
 * {@code llama-3.3-70b-versatile}, which intermittently emits a malformed tool call that its own
 * server rejects with {@code 400 tool_use_failed}):
 * <ol>
 *   <li><b>Short-circuit:</b> when a round only repeats tool calls already served this turn, stop
 *       offering tools and force a textual answer — cutting both wasted rounds and the surface for
 *       a malformed tool call.</li>
 *   <li><b>Resilience:</b> an invalid tool call, or any other model error, degrades to a tool-less
 *       retry and finally to a Spanish fallback message — the SSE stream never propagates the 400.</li>
 *   <li><b>Cache:</b> argument-deterministic regulatory searches are memoized via
 *       {@link ToolResultCache}, so repeats within and across turns skip the retrieval.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "assistant.tools.enabled", havingValue = "true")
public class AgenticAssistantLlmAdapter implements AssistantLlmPort {

    private static final Logger log = LoggerFactory.getLogger(AgenticAssistantLlmAdapter.class);

    private static final int MAX_OUTPUT_TOKENS = 400;
    private static final int MAX_TOOL_ROUNDS   = 5;

    private static final String FALLBACK_MESSAGE = "No he podido completar tu consulta en este "
            + "momento. Por favor, vuelve a intentarlo o reformula la pregunta.";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are BillMind, an expert assistant specialized in Spanish utility invoices \
            (electricity, gas, water, and telecom).
            Help users understand their invoices by answering questions clearly and accurately.

            Rules:
            1. The user's invoice data is provided below. Everything else — market rates, the price \
            comparison, regulatory information — must come from the tools available to you. \
            Never invent data.
            2. Act, never ask for permission. If answering needs data you do not have, retrieve it \
            in the same turn and reply with the result. Never ask the user whether you should look \
            something up, and never end an answer offering to retrieve something.
            3. The tools are an internal mechanism. Never mention them, never name them, and never \
            refer to "herramientas" or "funciones" in your answer. The user sees only the answer.
            4. When the user asks whether they are paying too much or which tariff is cheaper, use \
            the precomputed comparison and explain it. Do NOT rank raw market rates yourself.
            5. For questions about a specific company or tariff, look it up in the market rates.
            6. For questions about regulation, concepts or invoice terms, search the regulatory \
            knowledge base and cite the source document title returned (e.g. "según la guía de la \
            tarifa 2.0TD de REE").
            7. Missing data is a limit of BillMind's catalogue, never a fact about the world. If a \
            company has no rates, say you have no data for it and name the companies you do have. \
            Never state that a company or tariff does not exist.
            8. Only retrieve what you actually need; questions answerable from the invoice data \
            below need no retrieval.
            9. If neither the invoice nor the tools can answer, say so clearly and stop.
            10. Keep answers concise: maximum 3 short paragraphs. Use bullet points for lists.
            11. Never repeat the full invoice data back to the user.
            12. Respond in Spanish.

            --- DATOS DE LA FACTURA ---
            %s
            --- FIN FACTURA ---
            """;

    private final ChatModel smartChatModel;
    private final AssistantTools tools;
    private final ToolResultCache toolResultCache;

    public AgenticAssistantLlmAdapter(
            @Qualifier("smartChatModel") ChatModel smartChatModel,
            AssistantTools tools,
            ToolResultCache toolResultCache) {
        this.smartChatModel  = smartChatModel;
        this.tools           = tools;
        this.toolResultCache = toolResultCache;
    }

    @Override
    public ChatResult answer(ChatContext context, String question, List<ConversationMessage> history) {
        List<ChatMessage> messages = openingMessages(context, question, history);
        List<RegulatorySnippet> citationSink = new ArrayList<>();
        try {
            return runToolLoop(context, messages, citationSink);
        } catch (RuntimeException e) {
            log.error("[AGENT] unrecoverable error in agent loop: {}", e.getMessage(), e);
            return fallbackResult(citationSink);
        }
    }

    private ChatResult runToolLoop(ChatContext context, List<ChatMessage> messages,
                                   List<RegulatorySnippet> citationSink) {
        Set<String> servedThisTurn = new HashSet<>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            AiMessage ai;
            try {
                ai = smartChatModel.chat(withTools(messages)).aiMessage();
            } catch (InvalidRequestException e) {
                log.warn("[AGENT] model produced an invalid tool call ({}); forcing a tool-less answer",
                        e.getMessage());
                return forceFinalAnswer(messages, citationSink);
            }
            if (!ai.hasToolExecutionRequests()) {
                return finalResult(ai.text(), citationSink);
            }
            if (allAlreadyServed(ai, servedThisTurn)) {
                log.info("[AGENT][round={}] only repeats already-served tool calls; short-circuiting",
                        round + 1);
                return forceFinalAnswer(messages, citationSink);
            }
            messages.add(ai);
            executeToolRequests(ai, context, messages, citationSink, servedThisTurn, round);
        }
        // Rounds exhausted: force a final textual answer without offering more tools.
        log.warn("[AGENT] tool rounds exhausted (max={}); forcing a final answer without tools",
                MAX_TOOL_ROUNDS);
        return forceFinalAnswer(messages, citationSink);
    }

    private void executeToolRequests(AiMessage ai, ChatContext context, List<ChatMessage> messages,
                                     List<RegulatorySnippet> citationSink, Set<String> servedThisTurn,
                                     int round) {
        log.info("[AGENT][round={}] tool calls requested: {}", round + 1,
                ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList());
        for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
            String result = dispatchWithCache(req, context, citationSink);
            messages.add(ToolExecutionResultMessage.from(req, result));
            servedThisTurn.add(signature(req));
        }
    }

    /**
     * Dispatches a tool call, memoizing only {@code search_regulation} — the sole tool whose result
     * is a pure function of its arguments (invoice-independent), so it is safe to share across
     * sessions. Cached citations are re-added to this turn's sink so a cache hit keeps its sources.
     */
    private String dispatchWithCache(ToolExecutionRequest req, ChatContext context,
                                     List<RegulatorySnippet> citationSink) {
        if (!AssistantTools.SEARCH_REGULATION.equals(req.name())) {
            return tools.dispatch(req, context.invoiceFields(), citationSink);
        }
        String key = signature(req);
        Optional<CachedToolResult> cached = toolResultCache.get(key);
        if (cached.isPresent()) {
            log.debug("[AGENT] regulation cache hit for {}", key);
            citationSink.addAll(cached.get().citations());
            return cached.get().text();
        }
        List<RegulatorySnippet> callCitations = new ArrayList<>();
        String result = tools.dispatch(req, context.invoiceFields(), callCitations);
        citationSink.addAll(callCitations);
        toolResultCache.put(key, new CachedToolResult(result, callCitations));
        return result;
    }

    private ChatResult forceFinalAnswer(List<ChatMessage> messages, List<RegulatorySnippet> citationSink) {
        try {
            String answer = smartChatModel.chat(withoutTools(messages)).aiMessage().text();
            return finalResult(answer, citationSink);
        } catch (RuntimeException e) {
            log.error("[AGENT] tool-less fallback call failed: {}", e.getMessage(), e);
            return fallbackResult(citationSink);
        }
    }

    private ChatResult finalResult(String answer, List<RegulatorySnippet> citationSink) {
        List<ChatCitation> citations = toCitations(citationSink);
        log.info("[AGENT] answer produced ({} citations)", citations.size());
        log.debug("[AGENT] answer text: {}", answer);
        return new ChatResult(null, answer, citations);
    }

    private ChatResult fallbackResult(List<RegulatorySnippet> citationSink) {
        log.warn("[AGENT] returning degraded fallback answer");
        return new ChatResult(null, FALLBACK_MESSAGE, toCitations(citationSink));
    }

    /** True when every tool call in this round was already served earlier in the same turn. */
    private static boolean allAlreadyServed(AiMessage ai, Set<String> servedThisTurn) {
        return ai.toolExecutionRequests().stream()
                .allMatch(req -> servedThisTurn.contains(signature(req)));
    }

    /** Identity of a tool call by name + raw arguments; also the cache key for regulatory searches. */
    private static String signature(ToolExecutionRequest req) {
        String args = req.arguments() == null ? "" : req.arguments().strip();
        return req.name() + "|" + args;
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
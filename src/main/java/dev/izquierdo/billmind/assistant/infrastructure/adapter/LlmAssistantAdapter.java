package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmAssistantAdapter implements AssistantLlmPort {

    private static final String SYSTEM_PROMPT = """
            You are BillMind, an expert assistant specialized in Spanish utility invoices \
            (electricity, gas, water, and telecom).
            Help users understand their invoices by answering questions clearly and accurately.

            Rules:
            1. Base your answers ONLY on the invoice text and regulatory context provided. Do not invent data.
            2. When referencing regulatory information, cite the source document title \
            (e.g. "según la guía de la tarifa 2.0TD de REE").
            3. Use the actual figures from the user's invoice when available.
            4. If the question cannot be answered from the provided context, say so clearly.
            5. Keep answers structured and concise. Use bullet points for lists when appropriate.
            6. Respond in Spanish.
            """;

    private final ChatModel smartChatModel;

    public LlmAssistantAdapter(@Qualifier("smartChatModel") ChatModel smartChatModel) {
        this.smartChatModel = smartChatModel;
    }

    @Override
    public ChatResult answer(String invoiceText, List<RegulatorySnippet> context, String question) {
        String prompt = buildPrompt(invoiceText, context, question);
        String answer = smartChatModel.chat(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(prompt)
        ).aiMessage().text();

        List<ChatCitation> citations = context.stream()
                .map(r -> new ChatCitation(r.title(), r.source(), r.docType()))
                .distinct()
                .toList();

        return new ChatResult(answer, citations);
    }

    private String buildPrompt(String invoiceText, List<RegulatorySnippet> context, String question) {
        StringBuilder sb = new StringBuilder();

        sb.append("--- FACTURA DEL USUARIO ---\n");
        sb.append(invoiceText != null ? invoiceText : "No se ha proporcionado factura.");
        sb.append("\n--- FIN FACTURA ---\n\n");

        sb.append("--- CONTEXTO REGULATORIO ---\n");
        if (context.isEmpty()) {
            sb.append("Sin contexto regulatorio disponible.");
        } else {
            for (RegulatorySnippet chunk : context) {
                sb.append("[Fuente: ").append(chunk.title()).append("]\n");
                sb.append(chunk.content()).append("\n\n");
            }
        }
        sb.append("--- FIN CONTEXTO REGULATORIO ---\n\n");

        sb.append("--- PREGUNTA ---\n");
        sb.append(question).append("\n");
        sb.append("--- FIN PREGUNTA ---\n\n");

        sb.append("Responde en español.");
        return sb.toString();
    }
}
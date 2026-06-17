package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmAssistantAdapter implements AssistantLlmPort {

    private static final int MAX_OUTPUT_TOKENS = 400;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are BillMind, an expert assistant specialized in Spanish utility invoices \
            (electricity, gas, water, and telecom).
            Help users understand their invoices by answering questions clearly and accurately.

            Rules:
            1. Base your answers ONLY on the invoice data and regulatory context provided below. Do not invent data.
            2. When referencing regulatory information, cite the source document title \
            (e.g. "según la guía de la tarifa 2.0TD de REE").
            3. Use the actual figures from the user's invoice when available.
            4. If the question cannot be answered from the provided context, say so clearly.
            5. Keep answers concise: maximum 3 short paragraphs. Use bullet points for lists.
            6. Never repeat the full invoice data back to the user.
            7. Respond in Spanish.

            --- DATOS DE LA FACTURA ---
            %s
            --- FIN FACTURA ---

            --- CONTEXTO REGULATORIO ---
            %s
            --- FIN CONTEXTO REGULATORIO ---
            """;

    private final ChatModel smartChatModel;

    public LlmAssistantAdapter(@Qualifier("smartChatModel") ChatModel smartChatModel) {
        this.smartChatModel = smartChatModel;
    }

    @Override
    public ChatResult answer(InvoiceFields invoiceFields, List<RegulatorySnippet> context,
                             String question, List<ConversationMessage> history) {
        String systemContent = buildSystemContent(invoiceFields, context);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent));
        for (ConversationMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }
        messages.add(UserMessage.from(question));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .maxOutputTokens(MAX_OUTPUT_TOKENS)
                        .build())
                .build();

        String answer = smartChatModel.chat(chatRequest).aiMessage().text();

        List<ChatCitation> citations = context.stream()
                .map(r -> new ChatCitation(r.title(), r.source(), r.docType()))
                .distinct()
                .toList();

        return new ChatResult(null, answer, citations);
    }

    private String buildSystemContent(InvoiceFields invoiceFields, List<RegulatorySnippet> context) {
        String invoice = invoiceFields != null ? formatFields(invoiceFields) : "No se ha proporcionado factura.";

        String regulatory;
        if (context.isEmpty()) {
            regulatory = "Sin contexto regulatorio disponible.";
        } else {
            StringBuilder sb = new StringBuilder();
            for (RegulatorySnippet chunk : context) {
                sb.append("[Fuente: ").append(chunk.title()).append("]\n");
                sb.append(chunk.content()).append("\n\n");
            }
            regulatory = sb.toString().stripTrailing();
        }

        return SYSTEM_PROMPT_TEMPLATE.formatted(invoice, regulatory);
    }

    private String formatFields(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields e -> """
                    Tipo: Electricidad
                    Periodo: %s — %s
                    Importe total: %.2f €
                    Consumo total: %s kWh
                    Consumo P1/P2/P3: %s / %s / %s kWh
                    Precio P1/P2/P3: %s / %s / %s €/kWh
                    Potencia contratada: %s kW
                    """.formatted(
                    e.billingPeriodStart(), e.billingPeriodEnd(),
                    e.totalAmount(),
                    e.consumptionKwh(),
                    e.consumptionKwhP1(), e.consumptionKwhP2(), e.consumptionKwhP3(),
                    e.pricePerKwhP1(), e.pricePerKwhP2(), e.pricePerKwhP3(),
                    e.contractedPowerKw());
            case GasFields g -> """
                    Tipo: Gas
                    Periodo: %s — %s
                    Importe total: %.2f €
                    """.formatted(g.billingPeriodStart(), g.billingPeriodEnd(), g.totalAmount());
            case WaterFields w -> """
                    Tipo: Agua
                    Periodo: %s — %s
                    Importe total: %.2f €
                    """.formatted(w.billingPeriodStart(), w.billingPeriodEnd(), w.totalAmount());
            case TelecomFields t -> """
                    Tipo: Telecomunicaciones
                    Periodo: %s — %s
                    Importe total: %.2f €
                    """.formatted(t.billingPeriodStart(), t.billingPeriodEnd(), t.totalAmount());
        };
    }
}
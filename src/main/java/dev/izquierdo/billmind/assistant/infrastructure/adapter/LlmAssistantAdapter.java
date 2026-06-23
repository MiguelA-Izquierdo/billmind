package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult.ChatCitation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
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
            1. Base your answers ONLY on the invoice data, market rates, and regulatory context provided below. Do not invent data.
            2. When referencing regulatory information, cite the source document title \
            (e.g. "según la guía de la tarifa 2.0TD de REE").
            3. Use the actual figures from the user's invoice when available.
            4. When comparing prices, use the market rates provided.
            5. If the question cannot be answered from the provided context, say so clearly.
            6. Keep answers concise: maximum 3 short paragraphs. Use bullet points for lists.
            7. Never repeat the full invoice data back to the user.
            8. Respond in Spanish.

            --- DATOS DE LA FACTURA ---
            %s
            --- FIN FACTURA ---

            --- TARIFAS DE MERCADO ACTUALES ---
            %s
            --- FIN TARIFAS ---

            --- CONTEXTO REGULATORIO ---
            %s
            --- FIN CONTEXTO REGULATORIO ---
            """;

    private final ChatModel smartChatModel;

    public LlmAssistantAdapter(@Qualifier("smartChatModel") ChatModel smartChatModel) {
        this.smartChatModel = smartChatModel;
    }

    @Override
    public ChatResult answer(ChatContext context, String question, List<ConversationMessage> history) {
        String systemContent = buildSystemContent(context);

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

        List<ChatCitation> citations = context.regulatoryContext().stream()
                .map(r -> new ChatCitation(r.title(), r.source(), r.docType()))
                .distinct()
                .toList();

        return new ChatResult(null, answer, citations);
    }

    private String buildSystemContent(ChatContext context) {
        String invoice = context.invoiceFields() != null
                ? formatFields(context.invoiceFields()) : "No se ha proporcionado factura.";

        String market = formatMarketRates(context.marketRates());

        String regulatory;
        if (context.regulatoryContext().isEmpty()) {
            regulatory = "Sin contexto regulatorio disponible.";
        } else {
            StringBuilder sb = new StringBuilder();
            for (RegulatorySnippet chunk : context.regulatoryContext()) {
                sb.append("[Fuente: ").append(chunk.title()).append("]\n");
                sb.append(chunk.content()).append("\n\n");
            }
            regulatory = sb.toString().stripTrailing();
        }

        return SYSTEM_PROMPT_TEMPLATE.formatted(invoice, market, regulatory);
    }

    private String formatMarketRates(List<MarketRateSnapshot> rates) {
        if (rates.isEmpty()) return "Sin datos de mercado disponibles.";
        StringBuilder sb = new StringBuilder();
        for (MarketRateSnapshot r : rates) {
            sb.append(r.company()).append(" — ").append(r.tariffName())
              .append(" (vigente desde ").append(r.validFrom()).append(")\n");
            if (r.pricePerKwh() != null)
                sb.append("  Precio plano: ").append(r.pricePerKwh()).append(" €/kWh\n");
            if (r.pricePerKwhPunta() != null)
                sb.append("  P1 (punta): ").append(r.pricePerKwhPunta()).append(" €/kWh\n");
            if (r.pricePerKwhLlano() != null)
                sb.append("  P2 (llano): ").append(r.pricePerKwhLlano()).append(" €/kWh\n");
            if (r.pricePerKwhValle() != null)
                sb.append("  P3 (valle): ").append(r.pricePerKwhValle()).append(" €/kWh\n");
            if (r.contractedPowerPrice() != null)
                sb.append("  Potencia P1: ").append(r.contractedPowerPrice()).append(" €/kW/día\n");
            if (r.contractedPowerPriceP2() != null)
                sb.append("  Potencia P2: ").append(r.contractedPowerPriceP2()).append(" €/kW/día\n");
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
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
package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LlmAssistantAdapter implements AssistantLlmPort {

    private static final int MAX_OUTPUT_TOKENS = 400;

    private static final DecimalFormatSymbols SPANISH_SYMBOLS =
            new DecimalFormatSymbols(Locale.forLanguageTag("es-ES"));

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are BillMind, an expert assistant specialized in Spanish utility invoices \
            (electricity, gas, water, and telecom).
            Help users understand their invoices by answering questions clearly and accurately.

            Rules:
            1. Base your answers ONLY on the invoice data, market rates, and regulatory context provided below. Do not invent data.
            2. When referencing regulatory information, cite the source document title \
            (e.g. "según la guía de la tarifa 2.0TD de REE").
            3. Use the actual figures from the user's invoice when available.
            4. When the user asks whether they are paying too much or which tariff is cheaper, \
            base your answer on the "COMPARATIVA CALCULADA" section: the user's effective price, \
            the cheapest tariff and the annual savings are ALREADY computed there. \
            Do NOT re-rank or recompute the raw market rates yourself; just explain that result.
            5. Use the raw market rates list only for questions about a specific company or tariff.
            6. If the question cannot be answered from the provided context, say so clearly.
            7. Keep answers concise: maximum 3 short paragraphs. Use bullet points for lists.
            8. Never repeat the full invoice data back to the user.
            9. Respond in Spanish.

            --- DATOS DE LA FACTURA ---
            %s
            --- FIN FACTURA ---

            --- TARIFAS DE MERCADO ACTUALES ---
            %s
            --- FIN TARIFAS ---

            --- COMPARATIVA CALCULADA ---
            %s
            --- FIN COMPARATIVA ---

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

        String comparison = formatComparison(context.comparison());

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

        return SYSTEM_PROMPT_TEMPLATE.formatted(invoice, market, comparison, regulatory);
    }

    private String formatComparison(ComparisonSummary c) {
        if (c == null) {
            return "No hay comparativa disponible (la factura no tiene datos de precio o consumo suficientes).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Precio efectivo actual del usuario: ").append(num(c.userEffectivePricePerKwh()))
          .append(" €/kWh (").append(c.userIsTou() ? "tarifa por periodos" : "tarifa plana").append(").\n");
        sb.append("Consumo anual estimado: ").append(num(c.annualKwhEstimate())).append(" kWh.\n");
        appendOfferBlock(sb, "Mejor tarifa plana del mercado", c.flatBlock());
        appendOfferBlock(sb, "Mejor tarifa por periodos del mercado", c.touBlock());
        return sb.toString().stripTrailing();
    }

    private void appendOfferBlock(StringBuilder sb, String label, ComparisonSummary.OfferBlock block) {
        if (block == null) return;
        sb.append(label).append(": ").append(block.bestCompany()).append(" — ")
          .append(block.bestTariffName()).append(" a ").append(num(block.bestPricePerKwh())).append(" €/kWh.\n");
        if (block.annualSavingsEuros().signum() > 0) {
            sb.append("  Ahorro anual estimado frente a la tarifa actual: ")
              .append(eur(block.annualSavingsEuros())).append(" €.\n");
        } else {
            sb.append("  El usuario ya paga igual o menos que esta tarifa (sin ahorro).\n");
        }
        for (ComparisonSummary.Alternative a : block.alternatives()) {
            sb.append("  Alternativa: ").append(a.company()).append(" — ").append(a.tariffName())
              .append(": ").append(num(a.pricePerKwh())).append(" €/kWh\n");
        }
    }

    private String formatMarketRates(List<MarketRateSnapshot> rates) {
        if (rates.isEmpty()) return "Sin datos de mercado disponibles.";
        StringBuilder sb = new StringBuilder();
        for (MarketRateSnapshot r : rates) {
            sb.append(r.company()).append(" — ").append(r.tariffName())
              .append(" (vigente desde ").append(r.validFrom()).append(")\n");
            if (r.pricePerKwh() != null)
                sb.append("  Precio plano: ").append(num(r.pricePerKwh())).append(" €/kWh\n");
            if (r.pricePerKwhPunta() != null)
                sb.append("  P1 (punta): ").append(num(r.pricePerKwhPunta())).append(" €/kWh\n");
            if (r.pricePerKwhLlano() != null)
                sb.append("  P2 (llano): ").append(num(r.pricePerKwhLlano())).append(" €/kWh\n");
            if (r.pricePerKwhValle() != null)
                sb.append("  P3 (valle): ").append(num(r.pricePerKwhValle())).append(" €/kWh\n");
            if (r.contractedPowerPrice() != null)
                sb.append("  Potencia P1: ").append(num(r.contractedPowerPrice())).append(" €/kW/día\n");
            if (r.contractedPowerPriceP2() != null)
                sb.append("  Potencia P2: ").append(num(r.contractedPowerPriceP2())).append(" €/kW/día\n");
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String formatFields(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields e -> """
                    Tipo: Electricidad
                    Periodo: %s — %s
                    Importe total: %s €
                    Consumo total: %s kWh
                    Consumo P1/P2/P3: %s / %s / %s kWh
                    Precio P1/P2/P3: %s / %s / %s €/kWh
                    Potencia contratada: %s kW
                    """.formatted(
                    e.billingPeriodStart(), e.billingPeriodEnd(),
                    eur(e.totalAmount()),
                    num(e.consumptionKwh()),
                    num(e.consumptionKwhP1()), num(e.consumptionKwhP2()), num(e.consumptionKwhP3()),
                    num(e.pricePerKwhP1()), num(e.pricePerKwhP2()), num(e.pricePerKwhP3()),
                    num(e.contractedPowerKw()));
            case GasFields g -> """
                    Tipo: Gas
                    Periodo: %s — %s
                    Importe total: %s €
                    """.formatted(g.billingPeriodStart(), g.billingPeriodEnd(), eur(g.totalAmount()));
            case WaterFields w -> """
                    Tipo: Agua
                    Periodo: %s — %s
                    Importe total: %s €
                    """.formatted(w.billingPeriodStart(), w.billingPeriodEnd(), eur(w.totalAmount()));
            case TelecomFields t -> """
                    Tipo: Telecomunicaciones
                    Periodo: %s — %s
                    Importe total: %s €
                    """.formatted(t.billingPeriodStart(), t.billingPeriodEnd(), eur(t.totalAmount()));
        };
    }

    /** Formats a decimal for the Spanish prompt: comma separator, no grouping, trailing zeros stripped. */
    private static String num(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.######", SPANISH_SYMBOLS).format(value);
    }

    /** Formats a monetary amount with exactly two decimals and a Spanish comma separator. */
    private static String eur(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.00", SPANISH_SYMBOLS).format(value);
    }
}
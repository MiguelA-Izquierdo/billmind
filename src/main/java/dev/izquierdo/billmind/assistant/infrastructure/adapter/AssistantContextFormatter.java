package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind._shared.infrastructure.llm.prompt.PromptText;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Shared Spanish formatters for invoice fields, market rates and the deterministic comparison.
 * Used both by the eager {@link LlmAssistantAdapter} (which inlines everything in the system
 * prompt) and by the agentic tool dispatch (which formats tool results on demand). Centralizing
 * the €/kWh formatting avoids duplicating the locale-specific number rendering.
 */
public final class AssistantContextFormatter {

    private static final DecimalFormatSymbols SPANISH_SYMBOLS =
            new DecimalFormatSymbols(Locale.forLanguageTag("es-ES"));

    /** Real company and tariff names are short; anything longer is noise or an attempt at structure. */
    static final int MAX_NAME_CHARS = 60;

    private AssistantContextFormatter() {
    }

    /**
     * The two pricings are mutually exclusive by extraction contract: a flat or effective-average
     * invoice carries {@code pricePerKwh}, a TOU one carries the three period prices. Only the one
     * that applies is rendered — the summary used to print the period line alone, so every flat
     * invoice reached the assistant reading "Precio P1/P2/P3: — / — / —", i.e. announcing that the
     * single most relevant figure of the bill was missing when it had in fact been extracted.
     */
    private static String priceLine(ElectricityFields e) {
        if (e.pricePerKwh() != null) {
            return "Precio: " + num(e.pricePerKwh()) + " €/kWh";
        }
        return "Precio P1/P2/P3: %s / %s / %s €/kWh".formatted(
                num(e.pricePerKwhP1()), num(e.pricePerKwhP2()), num(e.pricePerKwhP3()));
    }

    /**
     * The power term, printed as the invoice bills it — 2.0TD charges punta and valle at different
     * daily prices. It is extracted, validated and persisted, so leaving it out sent every "¿cuánto
     * me cobran por la potencia?" through {@code search_invoice_text}, and with tools off nowhere.
     * Renders nothing when neither period carries a price: announcing a gap the model cannot fill
     * only adds a line it will repeat back.
     */
    private static String powerLine(ElectricityFields e) {
        BigDecimal p1 = e.powerPriceP1PerKwDay();
        BigDecimal p2 = e.powerPriceP2PerKwDay();
        if (p1 == null && p2 == null) return "";
        if (p1 == null || p2 == null) {
            return "Término de potencia: %s €/kW/día\n".formatted(num(p1 != null ? p1 : p2));
        }
        return "Término de potencia P1/P2: %s / %s €/kW/día\n".formatted(num(p1), num(p2));
    }

    public static String formatFields(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields e -> """
                    Tipo: Electricidad
                    Periodo: %s — %s
                    Importe total: %s €
                    Consumo total: %s kWh
                    Consumo P1/P2/P3: %s / %s / %s kWh
                    %s
                    Potencia contratada: %s kW
                    %s""".formatted(
                    e.billingPeriodStart(), e.billingPeriodEnd(),
                    eur(e.totalAmount()),
                    num(e.consumptionKwh()),
                    num(e.consumptionKwhP1()), num(e.consumptionKwhP2()), num(e.consumptionKwhP3()),
                    priceLine(e),
                    num(e.contractedPowerKw()),
                    powerLine(e));
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

    public static String formatMarketRates(List<MarketRateSnapshot> rates) {
        if (rates.isEmpty()) return "Sin datos de mercado disponibles.";
        StringBuilder sb = new StringBuilder();
        for (MarketRateSnapshot r : rates) {
            sb.append(name(r.company())).append(" — ").append(name(r.tariffName()))
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

    public static String formatComparison(ComparisonSummary c) {
        if (c == null) {
            return "No hay comparativa disponible (la factura no tiene datos de precio o consumo suficientes).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Precio efectivo actual del usuario: ").append(num(c.userEffectivePricePerKwh()))
          .append(" €/kWh (").append(c.userIsTou() ? "tarifa por periodos" : "tarifa plana").append(").\n");
        sb.append("Consumo anual estimado: ").append(num(c.annualKwhEstimate())).append(" kWh.\n");
        appendBasis(sb, c.basis());
        appendOfferBlock(sb, "Mejor tarifa plana del mercado", c.flatBlock(), c.invoiceTotalEuros());
        appendOfferBlock(sb, "Mejor tarifa por periodos del mercado", c.touBlock(), c.invoiceTotalEuros());
        return sb.toString().stripTrailing();
    }

    /**
     * States what the figures rest on. The model repeats this context almost verbatim, so anything
     * left unsaid here comes back to the user as a certainty the engine never claimed.
     */
    private static void appendBasis(StringBuilder sb, ComparisonSummary.Basis basis) {
        if (basis == null) return;
        sb.append("Base del cálculo: ").append(basis.observedDays()).append(" días facturados");
        sb.append(basis.annualised() ? ", extrapolados a un año.\n" : ".\n");
        if (!basis.powerTermIncluded()) {
            sb.append("  Solo compara el término de energía: el de potencia no se pudo leer de la factura.\n");
        } else if (basis.powerTermEstimated()) {
            sb.append("  Incluye el término de potencia, estimado a partir del total de la factura.\n");
        } else {
            sb.append("  Incluye el término de energía y el de potencia.\n");
        }
        if (basis.consumptionProfileAssumed()) {
            sb.append("  El reparto del consumo por periodos es un perfil doméstico estándar, no el de la factura.\n");
        }
        sb.append(basis.taxesIncluded() ? "  Las cifras llevan IVA e impuesto eléctrico.\n"
                                        : "  Los precios de la factura ya incluían impuestos.\n");
    }

    private static void appendOfferBlock(StringBuilder sb, String label,
                                         ComparisonSummary.OfferBlock block, BigDecimal invoiceTotal) {
        if (block == null) return;
        sb.append(label).append(": ").append(name(block.bestCompany())).append(" — ")
          .append(name(block.bestTariffName())).append(" a ").append(num(block.bestPricePerKwh())).append(" €/kWh.\n");
        appendPeriodSavings(sb, block, invoiceTotal);
        if (block.annualSavingsHigh().signum() > 0) {
            // A range, never a figure: the engine knows the two ends, not the point between them.
            sb.append("  Ahorro anual estimado frente a la tarifa actual: entre ")
              .append(eur(block.annualSavingsLow())).append(" € y ")
              .append(eur(block.annualSavingsHigh())).append(" €.\n");
        } else {
            sb.append("  El usuario ya paga igual o menos que esta tarifa (sin ahorro).\n");
        }
        for (ComparisonSummary.Alternative a : block.alternatives()) {
            sb.append("  Alternativa: ").append(name(a.company())).append(" — ").append(name(a.tariffName()))
              .append(": ").append(num(a.pricePerKwh())).append(" €/kWh\n");
        }
    }

    /**
     * The saving on the invoice the user is holding. Unlike the annual figure it extrapolates
     * nothing, so it is stated as a fact and paired with the printed total the user can check.
     * It is the number that earns the projection its credibility — state it first.
     */
    private static void appendPeriodSavings(StringBuilder sb, ComparisonSummary.OfferBlock block,
                                            BigDecimal invoiceTotal) {
        BigDecimal saving = block.periodSavingsEuros();
        if (saving == null || saving.signum() <= 0) return;
        sb.append("  En esta misma factura habría pagado ").append(eur(saving)).append(" € menos");
        if (invoiceTotal != null) {
            sb.append(": ").append(eur(invoiceTotal.subtract(saving)))
              .append(" € en lugar de ").append(eur(invoiceTotal)).append(" €");
        }
        sb.append(" (dato del periodo facturado, sin extrapolar).\n");
    }

    /**
     * Renders a company or tariff name. These arrive from the external Kafka producer, and the row
     * layout is line-based — an unflattened name could forge an extra tariff row. See PromptText.
     */
    public static String name(String value) {
        return PromptText.inline(value, MAX_NAME_CHARS);
    }

    /** Formats a decimal for the Spanish prompt: comma separator, no grouping, trailing zeros stripped. */
    public static String num(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.######", SPANISH_SYMBOLS).format(value);
    }

    /** Formats a monetary amount with exactly two decimals and a Spanish comma separator. */
    public static String eur(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.00", SPANISH_SYMBOLS).format(value);
    }
}
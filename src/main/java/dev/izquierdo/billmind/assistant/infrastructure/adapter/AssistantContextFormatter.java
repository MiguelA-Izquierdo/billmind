package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
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

    private AssistantContextFormatter() {
    }

    public static String formatFields(InvoiceFields fields) {
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

    public static String formatMarketRates(List<MarketRateSnapshot> rates) {
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

    public static String formatComparison(ComparisonSummary c) {
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

    private static void appendOfferBlock(StringBuilder sb, String label, ComparisonSummary.OfferBlock block) {
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
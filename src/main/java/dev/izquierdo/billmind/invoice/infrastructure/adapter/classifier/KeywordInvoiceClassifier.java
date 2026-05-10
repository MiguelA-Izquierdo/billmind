package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class KeywordInvoiceClassifier {

    private static final int MIN_SCORE = 2;

    private static final Map<InvoiceType, List<String>> KEYWORDS = Map.of(
        InvoiceType.LUZ,   List.of("cups", "kwh", "kw/h", "potencia contratada", "electricidad",
                                   "energía eléctrica", "término de energía", "peaje de acceso"),
        InvoiceType.GAS,   List.of("gas natural", "m³", "caudal", "termia", "pcs",
                                   "peaje de gas", "gj", "kwh gas"),
        InvoiceType.AGUA,  List.of("abastecimiento", "saneamiento", "agua potable",
                                   "contador de agua", "canon del agua", "cuota de servicio agua"),
        InvoiceType.TELCO, List.of("telefonía", "móvil", "gb datos", "datos nacionales",
                                   "llamadas", "minutos", "número de línea", "tarifa plana",
                                   "internet", "fibra", "roaming", "sms")
    );

    public Optional<InvoiceType> classify(String text) {
        String lower     = text.toLowerCase();
        InvoiceType best = null;
        int bestScore    = 0;

        for (Map.Entry<InvoiceType, List<String>> entry : KEYWORDS.entrySet()) {
            int score = countMatches(lower, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                best      = entry.getKey();
            }
        }

        return bestScore >= MIN_SCORE ? Optional.of(best) : Optional.empty();
    }

    private int countMatches(String text, List<String> keywords) {
        return (int) keywords.stream().filter(text::contains).count();
    }
}

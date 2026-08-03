package dev.izquierdo.billmind.assistant.infrastructure.adapter.tool;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keyword search over an invoice's redacted text, returning the matching lines and nothing else.
 *
 * <p>This is what lets the assistant reach the bill lines the extracted field set leaves out — the
 * power term, the electricity tax, the meter rental, a retailer's own discount. It returns
 * fragments, never the document, so a long PDF costs no more context than a short one; that is the
 * reason this is a tool and not another inlined context section.
 *
 * <p>Keyword-based on purpose. An invoice is a short, flat, line-oriented document whose concepts
 * are named almost verbatim in the question, and the model rewrites the user's wording (typos
 * included) into the search terms before this runs — so embeddings would buy relevance the text
 * already spells out.
 */
public final class InvoiceTextSearch {

    static final int MAX_MATCHES = 10;
    static final int MAX_RESULT_CHARS = 3_000;
    /** Shorter than this a term matches nearly every line and ranks nothing. */
    static final int MIN_TERM_LENGTH = 4;
    /** Match on a prefix so "contador" also finds "contadores", without fuzzy matching. */
    static final int STEM_LENGTH = 6;

    /** Question scaffolding long enough to survive the length filter. */
    private static final Set<String> STOP_WORDS =
            Set.of("para", "como", "cual", "esta", "este", "esto", "pago", "tiene", "cuanto", "donde");

    private InvoiceTextSearch() {
    }

    /**
     * Returns the lines matching {@code query}, the most relevant ones selected but printed in
     * document order so a multi-line block still reads top to bottom.
     */
    public static String search(String invoiceText, String query) {
        if (invoiceText == null || invoiceText.isBlank()) {
            return NO_TEXT;
        }
        List<String> stems = stems(query);
        if (stems.isEmpty()) {
            return TOO_GENERIC;
        }
        List<Match> matches = rank(invoiceText, stems);
        return matches.isEmpty() ? noMatch(query) : render(matches);
    }

    /** Accent-folded prefixes of the meaningful words in the query, in order and without repeats. */
    private static List<String> stems(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fold(query).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= MIN_TERM_LENGTH)
                .filter(term -> !STOP_WORDS.contains(term))
                .map(term -> term.length() > STEM_LENGTH ? term.substring(0, STEM_LENGTH) : term)
                .distinct()
                .toList();
    }

    /** Scores each line by how many distinct stems it carries; lines matching none are dropped. */
    private static List<Match> rank(String invoiceText, List<String> stems) {
        String[] lines = invoiceText.split("\\R");
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isBlank()) {
                continue;
            }
            String folded = fold(line);
            long score = stems.stream().filter(folded::contains).count();
            if (score > 0) {
                matches.add(new Match(i, score, line));
            }
        }
        return bestInDocumentOrder(matches);
    }

    /** Relevance decides which lines survive the cap; document order decides how they are printed. */
    private static List<Match> bestInDocumentOrder(List<Match> matches) {
        return matches.stream()
                .sorted(Comparator.comparingLong(Match::score).reversed()
                        .thenComparingInt(Match::index))
                .limit(MAX_MATCHES)
                .sorted(Comparator.comparingInt(Match::index))
                .toList();
    }

    private static String render(List<Match> matches) {
        StringBuilder sb = new StringBuilder("Líneas de la factura que coinciden con la búsqueda:\n");
        for (Match match : matches) {
            if (sb.length() + match.line().length() > MAX_RESULT_CHARS) {
                sb.append("[…]");
                break;
            }
            sb.append("- ").append(match.line()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * A miss means these words were not found, nothing more. Spelled out because a bare "no results"
     * is read as "the invoice does not charge for this" — the same inference that had the model
     * declaring companies non-existent (see {@code AssistantTools#notInCatalogue}).
     */
    private static String noMatch(String query) {
        return "No se han encontrado líneas en el texto de la factura con los términos de: '"
                + echo(query) + "'. Esto NO significa que el concepto no aparezca en la factura ni "
                + "que no se cobre: puede figurar con otro nombre. Vuelve a buscar con el término "
                + "tal y como suele venir impreso, por ejemplo 'potencia', 'impuesto', 'alquiler', "
                + "'equipo de medida' o 'descuento'.";
    }

    /** The query is the model's own argument coming back to it; keep it one short, inert line. */
    private static String echo(String query) {
        String single = query.replaceAll("\\s+", " ").strip();
        return single.length() > MAX_ECHO_CHARS ? single.substring(0, MAX_ECHO_CHARS) + "…" : single;
    }

    /** Lowercases and strips diacritics, so "eléctrico" matches "ELECTRICO". */
    private static String fold(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private static final int MAX_ECHO_CHARS = 80;

    private static final String NO_TEXT =
            "No hay texto de la factura disponible para esta consulta.";

    private static final String TOO_GENERIC = "La búsqueda no contiene ningún término concreto. "
            + "Repítela con la palabra que aparecería impresa en la factura, por ejemplo "
            + "'alquiler del contador' o 'impuesto eléctrico'.";

    private record Match(int index, long score, String line) {
    }
}
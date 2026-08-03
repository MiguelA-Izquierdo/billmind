package dev.izquierdo.billmind.assistant.infrastructure.adapter.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTextSearchTest {

    private static final String INVOICE = """
            COMERCIALIZADORA EJEMPLO - FACTURA ELECTRICIDAD
            Periodo de facturación: 01/03/2026 - 31/03/2026

            Término de potencia: 4,60 kW x 0,113000 €/kW/día ..... 15,59 €
            Energía consumida: 320 kWh x 0,148900 €/kWh .......... 47,65 €
            ALQUILER EQUIPOS DE MEDIDA .......................... 12,45 €
            Impuesto eléctrico 5,11% ............................. 3,23 €
            Descuento fidelidad -10% ............................ -4,76 €
            IVA 21% ............................................. 15,79 €
            TOTAL ............................................... 89,95 €
            """;

    @Test
    void shouldReturnOnlyTheMatchingLines() {
        String result = InvoiceTextSearch.search(INVOICE, "impuesto eléctrico");

        assertThat(result).contains("Impuesto eléctrico 5,11%").contains("3,23");
        assertThat(result).doesNotContain("TOTAL").doesNotContain("IVA 21%");
    }

    /** The question says "contador"; the bill says "EQUIPOS DE MEDIDA" and only shares "alquiler". */
    @Test
    void shouldMatchOnAnyTermSoAPartialWordingStillFindsTheLine() {
        String result = InvoiceTextSearch.search(INVOICE, "alquiler del contador");

        assertThat(result).contains("ALQUILER EQUIPOS DE MEDIDA");
    }

    @Test
    void shouldIgnoreCaseAndAccents() {
        assertThat(InvoiceTextSearch.search(INVOICE, "IMPUESTO ELECTRICO"))
                .contains("Impuesto eléctrico");
        assertThat(InvoiceTextSearch.search(INVOICE, "término de potencia"))
                .contains("Término de potencia");
    }

    /** The stem is what makes a plural in the question find a singular on the bill. */
    @Test
    void shouldMatchAcrossSingularAndPlural() {
        assertThat(InvoiceTextSearch.search(INVOICE, "descuentos")).contains("Descuento fidelidad");
        assertThat(InvoiceTextSearch.search("Contadores instalados: 1", "contador"))
                .contains("Contadores instalados");
    }

    @Test
    void shouldRankLinesMatchingMoreTermsFirstButPrintThemInDocumentOrder() {
        String text = """
                Linea A: potencia
                Linea B: nada relevante
                Linea C: potencia contratada facturada
                """;

        String result = InvoiceTextSearch.search(text, "potencia contratada");

        assertThat(result).doesNotContain("Linea B");
        assertThat(result.indexOf("Linea A")).isLessThan(result.indexOf("Linea C"));
    }

    /** A miss must not read as "the invoice does not charge for this" — see AssistantTools. */
    @Test
    void shouldDenyTheNonExistenceInferenceWhenNothingMatches() {
        String result = InvoiceTextSearch.search(INVOICE, "bono social");

        assertThat(result)
                .contains("No se han encontrado líneas")
                .contains("NO significa que el concepto no aparezca");
    }

    @Test
    void shouldAskForAConcreteTermWhenTheQueryCarriesNoUsableWord() {
        assertThat(InvoiceTextSearch.search(INVOICE, "que es por el de la"))
                .contains("no contiene ningún término concreto");
    }

    @Test
    void shouldReportNoTextWhenTheInvoiceHasNone() {
        assertThat(InvoiceTextSearch.search(null, "potencia")).contains("No hay texto de la factura");
        assertThat(InvoiceTextSearch.search("   ", "potencia")).contains("No hay texto de la factura");
    }

    @Test
    void shouldHandleNullAndBlankQueries() {
        assertThat(InvoiceTextSearch.search(INVOICE, null)).contains("no contiene ningún término");
        assertThat(InvoiceTextSearch.search(INVOICE, "  ")).contains("no contiene ningún término");
    }

    /** A long PDF must not blow the context: matches are capped whatever the document size. */
    @Test
    void shouldCapTheNumberOfLinesReturned() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            huge.append("Consumo periodo ").append(i).append(" ... 10,00 €\n");
        }

        String result = InvoiceTextSearch.search(huge.toString(), "consumo periodo");

        assertThat(result.lines().filter(l -> l.startsWith("- ")).count())
                .isEqualTo(InvoiceTextSearch.MAX_MATCHES);
    }

    @Test
    void shouldCapTheTotalResultLength() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            huge.append("Concepto facturado ").append("x".repeat(600)).append('\n');
        }

        String result = InvoiceTextSearch.search(huge.toString(), "concepto facturado");

        assertThat(result.length()).isLessThanOrEqualTo(InvoiceTextSearch.MAX_RESULT_CHARS + 200);
    }
}
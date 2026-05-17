package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfInvoiceParserIT {

    private final PdfInvoiceParser parser = new PdfInvoiceParser();

    @Test
    void shouldExtractNonEmptyTextFromPdf() throws Exception {
        byte[] pdf = buildPdf("CUPS ES0031 405131966\nPotencia contratada: 3,3 kW\nConsumo: 245 kWh\nImporte total: 85,40 EUR");

        String text = parser.extractText(pdf);

        assertThat(text).isNotBlank();
    }

    @Test
    void shouldPreserveRelevantContent() throws Exception {
        byte[] pdf = buildPdf("Potencia contratada: 3,3 kW\nConsumo: 245 kWh\nImporte total: 85,40 EUR");

        String text = parser.extractText(pdf);

        assertThat(text).contains("Potencia contratada").contains("Consumo").contains("Importe total");
    }

    @Test
    void shouldExtractTextFromMultiPagePdf() throws Exception {
        byte[] pdf = buildMultiPagePdf(
            "Página 1: datos del titular y contrato CUPS",
            "Página 2: desglose de consumo kWh potencia"
        );

        String text = parser.extractText(pdf);

        assertThat(text).contains("titular").contains("consumo");
    }

    @Test
    void shouldNormalizeFragmentedText() throws Exception {
        byte[] pdf = buildPdf("IB\nER\nDR\nOL\nA");

        String text = parser.extractText(pdf);

        assertThat(text).doesNotContain("\nI\nB");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] buildPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, text);
            return toBytes(doc);
        }
    }

    private byte[] buildMultiPagePdf(String... pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (String page : pages) addPage(doc, page);
            return toBytes(doc);
        }
    }

    private void addPage(PDDocument doc, String text) throws Exception {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 11);
            content.setLeading(16f);
            content.newLineAtOffset(50, 700);
            for (String line : text.split("\n")) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }

    private byte[] toBytes(PDDocument doc) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }
}
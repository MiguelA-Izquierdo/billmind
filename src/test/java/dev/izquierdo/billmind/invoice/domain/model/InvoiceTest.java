package dev.izquierdo.billmind.invoice.domain.model;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    @Test
    void shouldCreateInvoiceSuccessfully() {
        UUID id = UUID.randomUUID();
        Invoice invoice = Invoice.builder(id, "factura_enero.pdf").build();

        assertThat(invoice.getId()).isEqualTo(id);
        assertThat(invoice.getFileName()).isEqualTo("factura_enero.pdf");
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> Invoice.builder(null, "factura_enero.pdf").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Invoice ID cannot be null");
    }

    @Test
    void shouldThrowWhenFileNameIsNull() {
        assertThatThrownBy(() -> Invoice.builder(UUID.randomUUID(), null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("File name cannot be null");
    }

    @Test
    void shouldThrowWhenFileNameIsBlank() {
        assertThatThrownBy(() -> Invoice.builder(UUID.randomUUID(), "   ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSetUploadedAtOnCreation() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf").build();
        assertThat(invoice.getUploadedAt()).isNotNull();
    }

    @Test
    void shouldEnrichWithClassification() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf").build();
        InvoiceClassification classification = new InvoiceClassification(SupplyDomain.ELECTRICITY, "IBERDROLA");

        Invoice enriched = invoice.withClassification(classification);

        assertThat(enriched.getSupplyType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(enriched.getProvider()).isEqualTo("IBERDROLA");
    }

    @Test
    void shouldBeImmutableWhenEnriched() {
        Invoice original = Invoice.builder(UUID.randomUUID(), "factura.pdf").build();

        original.withClassification(new InvoiceClassification(SupplyDomain.GAS, "NATURGY"));

        assertThat(original.getSupplyType()).isNull();
        assertThat(original.getProvider()).isNull();
    }

    @Test
    void shouldPreserveIdAndFileNameAfterClassification() {
        UUID id = UUID.randomUUID();
        Invoice invoice = Invoice.builder(id, "factura_luz.pdf").build();

        Invoice enriched = invoice.withClassification(new InvoiceClassification(SupplyDomain.ELECTRICITY, "ENDESA"));

        assertThat(enriched.getId()).isEqualTo(id);
        assertThat(enriched.getFileName()).isEqualTo("factura_luz.pdf");
    }

    @Test
    void shouldEnrichWithSessionId() {
        UUID sessionId = UUID.randomUUID();
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf").build();

        Invoice withSession = invoice.withSessionId(sessionId);

        assertThat(withSession.getSessionId()).isEqualTo(sessionId);
        assertThat(invoice.getSessionId()).isNull();
    }

    @Test
    void shouldEnrichWithExtractedData() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf")
                .supplyType(SupplyDomain.ELECTRICITY)
                .build();
        ElectricityFields fields = new ElectricityFields(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                new BigDecimal("45.50"), new BigDecimal("405"), null, null, null,
                new BigDecimal("0.14"), null, null, null, new BigDecimal("3.3"));

        Invoice enriched = invoice.withExtractedData(fields, "texto redactado");

        assertThat(enriched.getFields()).isEqualTo(fields);
        assertThat(enriched.getRawTextRedacted()).isEqualTo("texto redactado");
    }

    @Test
    void shouldBeImmutableWhenEnrichedWithExtractedData() {
        Invoice original = Invoice.builder(UUID.randomUUID(), "factura.pdf").build();
        ElectricityFields fields = new ElectricityFields(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                new BigDecimal("45.50"), null, null, null, null, null, null, null, null, null);

        original.withExtractedData(fields, "texto");

        assertThat(original.getFields()).isNull();
        assertThat(original.getRawTextRedacted()).isNull();
    }
}

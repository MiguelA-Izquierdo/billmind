package dev.izquierdo.billmind.invoice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    @Test
    void shouldCreateInvoiceSuccessfully() {
        UUID id = UUID.randomUUID();
        Invoice invoice = new Invoice(id, "factura_enero.pdf");

        assertThat(invoice.getId()).isEqualTo(id);
        assertThat(invoice.getFileName()).isEqualTo("factura_enero.pdf");
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> new Invoice(null, "factura_enero.pdf"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Invoice ID cannot be null");
    }

    @Test
    void shouldThrowWhenFileNameIsNull() {
        assertThatThrownBy(() -> new Invoice(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("File name cannot be null");
    }
}

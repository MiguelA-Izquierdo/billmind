package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetInvoiceQueryTest {

    @Test
    void shouldCreateQueryWhenBothFieldsValid() {
        UUID invoiceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        GetInvoiceQuery query = new GetInvoiceQuery(invoiceId, sessionId);

        assertThat(query.invoiceId()).isEqualTo(invoiceId);
        assertThat(query.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void shouldThrowWhenInvoiceIdIsNull() {
        assertThatThrownBy(() -> new GetInvoiceQuery(null, UUID.randomUUID()))
                .isInstanceOf(ValidationErrorsException.class);
    }

    @Test
    void shouldThrowWhenSessionIdIsNull() {
        assertThatThrownBy(() -> new GetInvoiceQuery(UUID.randomUUID(), null))
                .isInstanceOf(ValidationErrorsException.class);
    }

    @Test
    void shouldThrowWhenBothFieldsAreNull() {
        assertThatThrownBy(() -> new GetInvoiceQuery(null, null))
                .isInstanceOf(ValidationErrorsException.class);
    }
}
package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetSessionInvoicesQueryTest {

    @Test
    void shouldCreateQueryWhenSessionIdValid() {
        UUID sessionId = UUID.randomUUID();

        GetSessionInvoicesQuery query = new GetSessionInvoicesQuery(sessionId);

        assertThat(query.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void shouldThrowWhenSessionIdIsNull() {
        assertThatThrownBy(() -> new GetSessionInvoicesQuery(null))
                .isInstanceOf(ValidationErrorsException.class);
    }
}
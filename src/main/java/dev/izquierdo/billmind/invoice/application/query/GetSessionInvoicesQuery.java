package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GetSessionInvoicesQuery(UUID sessionId) implements Query<List<Invoice>> {

    public GetSessionInvoicesQuery {
        if (sessionId == null) {
            throw new ValidationErrorsException(
                    Map.of("sessionId", Map.of("null", "El identificador de sesión no puede ser nulo"))
            );
        }
    }
}
package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record GetInvoiceQuery(UUID invoiceId, UUID sessionId) implements Query<Invoice> {

    public GetInvoiceQuery {
        Map<String, Map<String, String>> errors = new HashMap<>();
        if (invoiceId == null) {
            errors.put("invoiceId", Map.of("null", "El identificador de la factura no puede ser nulo"));
        }
        if (sessionId == null) {
            errors.put("sessionId", Map.of("null", "El identificador de sesión no puede ser nulo"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationErrorsException(errors);
        }
    }
}
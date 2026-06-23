package dev.izquierdo.billmind.comparison.application.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;

import java.util.Map;
import java.util.Optional;

public record CompareInvoiceQuery(InvoiceFields fields) implements Query<Optional<ComparisonResult>> {

    public CompareInvoiceQuery {
        if (fields == null) {
            throw new ValidationErrorsException(Map.of(
                    "fields", Map.of("null", "Los datos de la factura no pueden ser nulos")));
        }
    }
}

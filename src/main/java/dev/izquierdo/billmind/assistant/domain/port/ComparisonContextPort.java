package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;

import java.util.Optional;

public interface ComparisonContextPort {
    Optional<ComparisonSummary> summarize(InvoiceFields fields);
}
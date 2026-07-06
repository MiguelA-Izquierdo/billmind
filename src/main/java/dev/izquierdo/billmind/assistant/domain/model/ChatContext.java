package dev.izquierdo.billmind.assistant.domain.model;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

import java.util.List;

public record ChatContext(
    InvoiceFields invoiceFields,
    List<RegulatorySnippet> regulatoryContext,
    List<MarketRateSnapshot> marketRates,
    ComparisonSummary comparison
) {}
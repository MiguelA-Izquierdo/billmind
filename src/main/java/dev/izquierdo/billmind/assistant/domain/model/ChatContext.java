package dev.izquierdo.billmind.assistant.domain.model;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

import java.util.List;

public record ChatContext(
    InvoiceFields invoiceFields,
    List<RegulatorySnippet> regulatoryContext,
    List<MarketRateSnapshot> marketRates,
    ComparisonSummary comparison
) {
    public ChatContext {
        regulatoryContext = regulatoryContext == null ? List.of() : regulatoryContext;
        marketRates       = marketRates == null ? List.of() : marketRates;
    }

    /** Full eager context: invoice plus regulatory snippets, market rates and comparison. */
    public static ChatContext eager(
            InvoiceFields invoiceFields,
            List<RegulatorySnippet> regulatoryContext,
            List<MarketRateSnapshot> marketRates,
            ComparisonSummary comparison) {
        return new ChatContext(invoiceFields, regulatoryContext, marketRates, comparison);
    }

    /**
     * Agentic mode: only the invoice is inlined; the LLM pulls regulation, market rates and the
     * comparison on demand through tools, so those slots are intentionally empty.
     */
    public static ChatContext invoiceOnly(InvoiceFields invoiceFields) {
        return new ChatContext(invoiceFields, List.of(), List.of(), null);
    }
}
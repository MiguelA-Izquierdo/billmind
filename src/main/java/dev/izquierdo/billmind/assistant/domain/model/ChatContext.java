package dev.izquierdo.billmind.assistant.domain.model;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

import java.util.List;

/**
 * @param invoiceText the invoice's redacted full text, carried for the search tool to read on
 *                    demand. It is never formatted into a prompt: the extracted fields are what
 *                    gets inlined, and this is only ever reached one searched fragment at a time.
 */
public record ChatContext(
    InvoiceFields invoiceFields,
    String invoiceText,
    List<RegulatorySnippet> regulatoryContext,
    List<MarketRateSnapshot> marketRates,
    ComparisonSummary comparison
) {
    public ChatContext {
        regulatoryContext = regulatoryContext == null ? List.of() : regulatoryContext;
        marketRates       = marketRates == null ? List.of() : marketRates;
    }

    /**
     * Full eager context: invoice plus regulatory snippets, market rates and comparison. Carries no
     * invoice text — without tools there is nothing that could search it.
     */
    public static ChatContext eager(
            InvoiceFields invoiceFields,
            List<RegulatorySnippet> regulatoryContext,
            List<MarketRateSnapshot> marketRates,
            ComparisonSummary comparison) {
        return new ChatContext(invoiceFields, null, regulatoryContext, marketRates, comparison);
    }

    /**
     * Agentic mode: only the invoice is inlined; the LLM pulls regulation, market rates and the
     * comparison on demand through tools, so those slots are intentionally empty.
     */
    public static ChatContext invoiceOnly(InvoiceFields invoiceFields, String invoiceText) {
        return new ChatContext(invoiceFields, invoiceText, List.of(), List.of(), null);
    }
}
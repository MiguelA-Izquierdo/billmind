package dev.izquierdo.billmind.assistant.application.service;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.ComparisonContextPort;
import dev.izquierdo.billmind.assistant.domain.port.InvoiceContextPort;
import dev.izquierdo.billmind.assistant.domain.port.MarketRatesContextPort;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatContextAssembler {

    private final InvoiceContextPort invoiceContextPort;
    private final RegulationSearchPort regulationSearchPort;
    private final MarketRatesContextPort marketRatesContextPort;
    private final ComparisonContextPort comparisonContextPort;
    private final int maxKnowledgeResults;
    private final boolean toolsEnabled;

    public ChatContextAssembler(
            InvoiceContextPort invoiceContextPort,
            RegulationSearchPort regulationSearchPort,
            MarketRatesContextPort marketRatesContextPort,
            ComparisonContextPort comparisonContextPort,
            @Value("${knowledge.search.default-max-results:5}") int maxKnowledgeResults,
            @Value("${assistant.tools.enabled:false}") boolean toolsEnabled) {
        this.invoiceContextPort     = Objects.requireNonNull(invoiceContextPort);
        this.regulationSearchPort   = Objects.requireNonNull(regulationSearchPort);
        this.marketRatesContextPort = Objects.requireNonNull(marketRatesContextPort);
        this.comparisonContextPort  = Objects.requireNonNull(comparisonContextPort);
        this.maxKnowledgeResults    = maxKnowledgeResults;
        this.toolsEnabled           = toolsEnabled;
    }

    public ChatContext assemble(UUID invoiceId, String question) {
        InvoiceFields invoiceFields = invoiceId != null
                ? invoiceContextPort.loadInvoice(invoiceId).map(Invoice::getFields).orElse(null)
                : null;

        // In agentic mode the LLM pulls regulation, market rates and comparison on demand via
        // tools, so we skip the eager (and costly) loads and inline only the invoice.
        if (toolsEnabled) {
            return ChatContext.invoiceOnly(invoiceFields);
        }

        List<RegulatorySnippet> regulatory = regulationSearchPort.search(question, maxKnowledgeResults);

        List<MarketRateSnapshot> marketRates = invoiceFields != null
                ? marketRatesContextPort.loadLatestRates(supplyDomainOf(invoiceFields))
                : List.of();

        ComparisonSummary comparison = invoiceFields != null
                ? comparisonContextPort.summarize(invoiceFields).orElse(null)
                : null;

        return ChatContext.eager(invoiceFields, regulatory, marketRates, comparison);
    }

    private static SupplyDomain supplyDomainOf(InvoiceFields fields) {
        return switch (fields) {
            case ElectricityFields ignored -> SupplyDomain.ELECTRICITY;
            case GasFields ignored         -> SupplyDomain.GAS;
            case WaterFields ignored       -> SupplyDomain.WATER;
            case TelecomFields ignored     -> SupplyDomain.TELECOM;
        };
    }
}
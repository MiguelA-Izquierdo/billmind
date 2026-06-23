package dev.izquierdo.billmind.assistant.application.service;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
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
    private final int maxKnowledgeResults;

    public ChatContextAssembler(
            InvoiceContextPort invoiceContextPort,
            RegulationSearchPort regulationSearchPort,
            MarketRatesContextPort marketRatesContextPort,
            @Value("${knowledge.search.default-max-results:5}") int maxKnowledgeResults) {
        this.invoiceContextPort     = Objects.requireNonNull(invoiceContextPort);
        this.regulationSearchPort   = Objects.requireNonNull(regulationSearchPort);
        this.marketRatesContextPort = Objects.requireNonNull(marketRatesContextPort);
        this.maxKnowledgeResults    = maxKnowledgeResults;
    }

    public ChatContext assemble(UUID invoiceId, String question) {
        InvoiceFields invoiceFields = invoiceId != null
                ? invoiceContextPort.loadInvoice(invoiceId).map(Invoice::getFields).orElse(null)
                : null;

        List<RegulatorySnippet> regulatory = regulationSearchPort.search(question, maxKnowledgeResults);

        List<MarketRateSnapshot> marketRates = invoiceFields != null
                ? marketRatesContextPort.loadLatestRates(supplyDomainOf(invoiceFields))
                : List.of();

        return new ChatContext(invoiceFields, regulatory, marketRates);
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
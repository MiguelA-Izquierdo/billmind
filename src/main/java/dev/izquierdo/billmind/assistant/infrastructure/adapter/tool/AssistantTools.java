package dev.izquierdo.billmind.assistant.infrastructure.adapter.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.ComparisonContextPort;
import dev.izquierdo.billmind.assistant.domain.port.MarketRatesContextPort;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.izquierdo.billmind.assistant.infrastructure.adapter.AssistantContextFormatter;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Catalogue and dispatcher for the assistant's agentic tools. Exposes the three retrieval
 * capabilities the LLM can call on demand — deterministic comparison, market rates and
 * regulatory search — instead of receiving them eagerly in the system prompt. Only wired
 * when {@code assistant.tools.enabled=true}; see {@code AgenticAssistantLlmAdapter}.
 */
@Component
@ConditionalOnProperty(name = "assistant.tools.enabled", havingValue = "true")
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    static final String GET_INVOICE_COMPARISON = "get_invoice_comparison";
    static final String SEARCH_MARKET_RATES    = "search_market_rates";
    /** Public so the agentic adapter can tell which tool is safe to cache (argument-deterministic). */
    public static final String SEARCH_REGULATION = "search_regulation";

    private static final String NO_INVOICE = "No se ha proporcionado factura, así que no hay datos "
            + "de la factura del usuario para esta consulta.";

    private static final String NO_RATES = "BillMind no tiene ninguna tarifa de mercado registrada "
            + "para este tipo de suministro.";

    private final ComparisonContextPort comparisonContextPort;
    private final MarketRatesContextPort marketRatesContextPort;
    private final RegulationSearchPort regulationSearchPort;
    private final ObjectMapper objectMapper;
    private final int maxKnowledgeResults;

    public AssistantTools(
            ComparisonContextPort comparisonContextPort,
            MarketRatesContextPort marketRatesContextPort,
            RegulationSearchPort regulationSearchPort,
            ObjectMapper objectMapper,
            @Value("${knowledge.search.default-max-results:5}") int maxKnowledgeResults) {
        this.comparisonContextPort  = comparisonContextPort;
        this.marketRatesContextPort = marketRatesContextPort;
        this.regulationSearchPort   = regulationSearchPort;
        this.objectMapper           = objectMapper;
        this.maxKnowledgeResults    = maxKnowledgeResults;
    }

    /** The tool catalogue advertised to the model on every agentic round. */
    public List<ToolSpecification> specifications() {
        ToolSpecification comparison = ToolSpecification.builder()
                .name(GET_INVOICE_COMPARISON)
                .description("Devuelve la comparativa determinista ya calculada de la factura del usuario: "
                        + "su precio efectivo, la tarifa más barata del mercado y el ahorro anual estimado. "
                        + "Úsala cuando el usuario pregunte si está pagando de más o qué tarifa le conviene.")
                .parameters(JsonObjectSchema.builder().build())
                .build();

        ToolSpecification marketRates = ToolSpecification.builder()
                .name(SEARCH_MARKET_RATES)
                .description("Lista las tarifas actuales del mercado para el tipo de suministro de la factura. "
                        + "Úsala para preguntas sobre una compañía o tarifa concreta.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("company",
                                "Nombre (o parte) de la compañía para filtrar, p. ej. 'Naturgy'. Opcional.")
                        .build())
                .build();

        ToolSpecification regulation = ToolSpecification.builder()
                .name(SEARCH_REGULATION)
                .description("Busca en la base de conocimiento regulatoria (CNMC, REE, BOE) fragmentos "
                        + "relevantes. Úsala para dudas sobre conceptos, normativa o términos de la factura, "
                        + "p. ej. 'qué es el término de potencia'.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Consulta en lenguaje natural para la búsqueda semántica.")
                        .required("query")
                        .build())
                .build();

        return List.of(comparison, marketRates, regulation);
    }

    /**
     * Executes the tool named by {@code request} and returns text the model reads as the tool result.
     * Snippets actually retrieved by {@link #SEARCH_REGULATION} are appended to {@code citationSink}
     * so the turn's citations reflect only what was really used.
     */
    public String dispatch(ToolExecutionRequest request, InvoiceFields fields,
                           List<RegulatorySnippet> citationSink) {
        String name = request.name();
        log.debug("[TOOL] dispatch name={} arguments={}", name, request.arguments());
        return switch (name) {
            case GET_INVOICE_COMPARISON -> comparison(fields);
            case SEARCH_MARKET_RATES    -> marketRates(fields, request);
            case SEARCH_REGULATION      -> regulation(request, citationSink);
            default -> "Herramienta desconocida: " + name;
        };
    }

    private String comparison(InvoiceFields fields) {
        if (fields == null) return NO_INVOICE;
        Optional<ComparisonSummary> summary = comparisonContextPort.summarize(fields);
        return AssistantContextFormatter.formatComparison(summary.orElse(null));
    }

    private String marketRates(InvoiceFields fields, ToolExecutionRequest request) {
        if (fields == null) return NO_INVOICE;
        List<MarketRateSnapshot> rates = marketRatesContextPort.loadLatestRates(supplyDomainOf(fields));
        if (rates.isEmpty()) return NO_RATES;
        String company = readStringArgument(request, "company");
        if (company == null || company.isBlank()) {
            return AssistantContextFormatter.formatMarketRates(rates);
        }
        String needle = company.toLowerCase(Locale.ROOT).strip();
        List<MarketRateSnapshot> matches = rates.stream()
                .filter(r -> r.company() != null
                        && r.company().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        return matches.isEmpty()
                ? notInCatalogue(company, rates)
                : AssistantContextFormatter.formatMarketRates(matches);
    }

    /**
     * Absence from BillMind's catalogue is a limitation of our data, never a fact about the market.
     * The denial is spelled out — and paired with the companies we do have — because models read a
     * bare "no rates found" as "the company does not exist" (observed with llama-3.3-70b-versatile,
     * which told a user that Octopus does not operate in Spain).
     */
    private static String notInCatalogue(String company, List<MarketRateSnapshot> rates) {
        return "BillMind no tiene tarifas registradas de '" + company + "' en su catálogo. Esto NO "
                + "significa que la compañía no exista ni que no opere en España: simplemente no "
                + "está entre los datos de mercado disponibles. Compañías con tarifas en el "
                + "catálogo: " + companiesIn(rates) + ".";
    }

    private static String companiesIn(List<MarketRateSnapshot> rates) {
        return rates.stream()
                .map(MarketRateSnapshot::company)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String regulation(ToolExecutionRequest request, List<RegulatorySnippet> citationSink) {
        String query = readStringArgument(request, "query");
        if (query == null || query.isBlank()) {
            return "Falta el parámetro 'query' para buscar en la normativa.";
        }
        List<RegulatorySnippet> snippets = regulationSearchPort.search(query, maxKnowledgeResults);
        citationSink.addAll(snippets);
        if (snippets.isEmpty()) {
            return "Sin resultados regulatorios para: " + query;
        }
        StringBuilder sb = new StringBuilder();
        for (RegulatorySnippet s : snippets) {
            sb.append("[Fuente: ").append(s.title()).append("]\n").append(s.content()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    /** Tolerant read of a string argument: returns null on missing key, blank JSON or parse errors. */
    private String readStringArgument(ToolExecutionRequest request, String key) {
        String arguments = request.arguments();
        if (arguments == null || arguments.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(arguments).get(key);
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (Exception e) {
            log.warn("[TOOL] could not parse arguments for key={} arguments={}", key, arguments);
            return null;
        }
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
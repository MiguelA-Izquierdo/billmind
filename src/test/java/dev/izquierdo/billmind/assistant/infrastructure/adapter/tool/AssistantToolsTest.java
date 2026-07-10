package dev.izquierdo.billmind.assistant.infrastructure.adapter.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.ComparisonContextPort;
import dev.izquierdo.billmind.assistant.domain.port.MarketRatesContextPort;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolsTest {

    @Mock private ComparisonContextPort comparisonContextPort;
    @Mock private MarketRatesContextPort marketRatesContextPort;
    @Mock private RegulationSearchPort regulationSearchPort;

    private AssistantTools tools;

    private static final int MAX_RESULTS = 5;

    private static final ElectricityFields FIELDS = new ElectricityFields(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), new BigDecimal("45.50"),
            null, null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        tools = new AssistantTools(comparisonContextPort, marketRatesContextPort,
                regulationSearchPort, new ObjectMapper(), MAX_RESULTS);
    }

    private static ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder().id("call-1").name(name).arguments(arguments).build();
    }

    private static MarketRateSnapshot rate(String company, String tariff) {
        return new MarketRateSnapshot(company, tariff, new BigDecimal("0.12"),
                null, null, null, null, null, LocalDate.of(2024, 1, 1));
    }

    // --- catalogue ---

    @Test
    void shouldExposeThreeToolsWithExpectedNames() {
        List<String> names = tools.specifications().stream().map(ToolSpecification::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "get_invoice_comparison", "search_market_rates", "search_regulation");
    }

    @Test
    void shouldMarkQueryAsRequiredOnlyForRegulationTool() {
        ToolSpecification regulation = specificationNamed("search_regulation");
        ToolSpecification marketRates = specificationNamed("search_market_rates");

        assertThat(regulation.parameters().required()).containsExactly("query");
        assertThat(marketRates.parameters().required()).isNullOrEmpty();
    }

    private ToolSpecification specificationNamed(String name) {
        return tools.specifications().stream()
                .filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    // --- get_invoice_comparison ---

    @Test
    void shouldSummarizeComparisonForInvoiceFields() {
        ComparisonSummary summary = new ComparisonSummary(
                new BigDecimal("0.123"), true, new BigDecimal("3869.00"), null, null);
        when(comparisonContextPort.summarize(FIELDS)).thenReturn(Optional.of(summary));

        String result = tools.dispatch(request("get_invoice_comparison", "{}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Precio efectivo actual del usuario");
        verify(comparisonContextPort).summarize(FIELDS);
    }

    @Test
    void shouldReportNoInvoiceWhenComparisonRequestedWithoutFields() {
        String result = tools.dispatch(request("get_invoice_comparison", "{}"), null, new ArrayList<>());

        assertThat(result).contains("No se ha proporcionado factura");
        verify(comparisonContextPort, never()).summarize(org.mockito.ArgumentMatchers.any());
    }

    // --- search_market_rates ---

    @Test
    void shouldFilterMarketRatesByCompanySubstringCaseInsensitive() {
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY))
                .thenReturn(List.of(rate("Naturgy", "Plan A"), rate("Iberdrola", "Plan B")));

        String result = tools.dispatch(
                request("search_market_rates", "{\"company\":\"naturgy\"}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Naturgy").doesNotContain("Iberdrola");
    }

    @Test
    void shouldReturnAllRatesWhenNoCompanyArgument() {
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY))
                .thenReturn(List.of(rate("Naturgy", "Plan A"), rate("Iberdrola", "Plan B")));

        String result = tools.dispatch(request("search_market_rates", "{}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Naturgy").contains("Iberdrola");
    }

    @Test
    void shouldDenyNonExistenceAndListAvailableCompaniesWhenCompanyIsNotInCatalogue() {
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY))
                .thenReturn(List.of(rate("Naturgy", "Plan A"), rate("Endesa", "Plan B"),
                        rate("Endesa", "Plan C")));

        String result = tools.dispatch(
                request("search_market_rates", "{\"company\":\"Octopus\"}"), FIELDS, new ArrayList<>());

        // The model must not read a missing company as "does not operate in Spain".
        assertThat(result)
                .contains("no tiene tarifas registradas de 'Octopus'")
                .contains("NO significa que la compañía no exista")
                .contains("Endesa, Naturgy"); // deduplicated and alphabetical
    }

    @Test
    void shouldReportEmptyCatalogueWhenNoRatesExistForTheSupplyDomain() {
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY)).thenReturn(List.of());

        String result = tools.dispatch(
                request("search_market_rates", "{\"company\":\"Endesa\"}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("no tiene ninguna tarifa de mercado registrada");
    }

    // --- search_regulation ---

    @Test
    void shouldSearchRegulationAndAccumulateCitations() {
        RegulatorySnippet snippet = new RegulatorySnippet("Guía 2.0TD", "REE", "GUIDE", "contenido");
        when(regulationSearchPort.search("término de potencia", MAX_RESULTS)).thenReturn(List.of(snippet));
        List<RegulatorySnippet> sink = new ArrayList<>();

        String result = tools.dispatch(
                request("search_regulation", "{\"query\":\"término de potencia\"}"), FIELDS, sink);

        assertThat(result).contains("Guía 2.0TD").contains("contenido");
        assertThat(sink).containsExactly(snippet);
        verify(regulationSearchPort).search("término de potencia", MAX_RESULTS);
    }

    @Test
    void shouldReportMissingQueryWhenRegulationCalledWithoutQuery() {
        String result = tools.dispatch(request("search_regulation", "{}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Falta el parámetro 'query'");
        verify(regulationSearchPort, never()).search(org.mockito.ArgumentMatchers.any(), eq(MAX_RESULTS));
    }

    @Test
    void shouldReportNoResultsWhenRegulationSearchIsEmpty() {
        when(regulationSearchPort.search("inexistente", MAX_RESULTS)).thenReturn(List.of());

        String result = tools.dispatch(
                request("search_regulation", "{\"query\":\"inexistente\"}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Sin resultados regulatorios");
    }

    // --- robustness ---

    @Test
    void shouldReturnUnknownToolMessageForUnrecognizedName() {
        String result = tools.dispatch(request("do_something_else", "{}"), FIELDS, new ArrayList<>());

        assertThat(result).contains("Herramienta desconocida");
    }

    @Test
    void shouldTolerateMalformedArgumentsJson() {
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY))
                .thenReturn(List.of(rate("Naturgy", "Plan A")));

        String result = tools.dispatch(
                request("search_market_rates", "not-json"), FIELDS, new ArrayList<>());

        // Malformed args -> company treated as absent -> all rates returned.
        assertThat(result).contains("Naturgy");
    }
}
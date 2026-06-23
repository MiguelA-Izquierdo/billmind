package dev.izquierdo.billmind.assistant.application.service;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.MarketRateSnapshot;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.InvoiceContextPort;
import dev.izquierdo.billmind.assistant.domain.port.MarketRatesContextPort;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatContextAssemblerTest {

    @Mock private InvoiceContextPort    invoiceContextPort;
    @Mock private RegulationSearchPort  regulationSearchPort;
    @Mock private MarketRatesContextPort marketRatesContextPort;

    private ChatContextAssembler assembler;

    private static final UUID   INVOICE_ID = UUID.randomUUID();
    private static final String QUESTION   = "¿Cuánto pago de potencia?";
    private static final int    MAX_RESULTS = 5;

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 1, 31);

    private static final ElectricityFields ELECTRICITY_FIELDS =
            new ElectricityFields(START, END, new BigDecimal("45.50"),
                    null, null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        assembler = new ChatContextAssembler(
                invoiceContextPort, regulationSearchPort, marketRatesContextPort, MAX_RESULTS);
    }

    // --- invoice fields ---

    @Test
    void shouldLoadInvoiceFieldsWhenInvoiceIdProvided() {
        Invoice invoice = Invoice.builder(INVOICE_ID, "factura.pdf").fields(ELECTRICITY_FIELDS).build();
        when(invoiceContextPort.loadInvoice(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());
        when(marketRatesContextPort.loadLatestRates(any())).thenReturn(List.of());

        ChatContext context = assembler.assemble(INVOICE_ID, QUESTION);

        assertThat(context.invoiceFields()).isEqualTo(ELECTRICITY_FIELDS);
    }

    @Test
    void shouldSetNullInvoiceFieldsWhenInvoiceIdIsNull() {
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());

        ChatContext context = assembler.assemble(null, QUESTION);

        assertThat(context.invoiceFields()).isNull();
        verify(invoiceContextPort, never()).loadInvoice(any());
    }

    @Test
    void shouldSetNullInvoiceFieldsWhenInvoiceNotFound() {
        when(invoiceContextPort.loadInvoice(INVOICE_ID)).thenReturn(Optional.empty());
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());

        ChatContext context = assembler.assemble(INVOICE_ID, QUESTION);

        assertThat(context.invoiceFields()).isNull();
    }

    // --- regulatory context ---

    @Test
    void shouldSearchRegulatoryContextWithQuestionAndMaxResults() {
        RegulatorySnippet snippet = new RegulatorySnippet("Guía 2.0TD", "REE", "GUIDE", "contenido");
        when(invoiceContextPort.loadInvoice(any())).thenReturn(Optional.empty());
        when(regulationSearchPort.search(QUESTION, MAX_RESULTS)).thenReturn(List.of(snippet));

        ChatContext context = assembler.assemble(INVOICE_ID, QUESTION);

        assertThat(context.regulatoryContext()).containsExactly(snippet);
        verify(regulationSearchPort).search(QUESTION, MAX_RESULTS);
    }

    @Test
    void shouldSearchRegulatoryContextEvenWithoutInvoiceId() {
        RegulatorySnippet snippet = new RegulatorySnippet("Glosario", "BillMind", "GLOSSARY", "texto");
        when(regulationSearchPort.search(QUESTION, MAX_RESULTS)).thenReturn(List.of(snippet));

        ChatContext context = assembler.assemble(null, QUESTION);

        assertThat(context.regulatoryContext()).containsExactly(snippet);
    }

    // --- market rates ---

    @Test
    void shouldLoadMarketRatesWithElectricityDomainForElectricityInvoice() {
        Invoice invoice = Invoice.builder(INVOICE_ID, "factura.pdf").fields(ELECTRICITY_FIELDS).build();
        MarketRateSnapshot rate = new MarketRateSnapshot("Iberdrola", "2.0TD",
                null, new BigDecimal("0.08"), new BigDecimal("0.11"), new BigDecimal("0.18"),
                new BigDecimal("0.044"), null, START);
        when(invoiceContextPort.loadInvoice(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.ELECTRICITY)).thenReturn(List.of(rate));

        ChatContext context = assembler.assemble(INVOICE_ID, QUESTION);

        assertThat(context.marketRates()).containsExactly(rate);
        verify(marketRatesContextPort).loadLatestRates(SupplyDomain.ELECTRICITY);
    }

    @Test
    void shouldLoadMarketRatesWithGasDomainForGasInvoice() {
        GasFields gasFields = new GasFields(START, END, new BigDecimal("60.00"), null, null, null);
        Invoice invoice = Invoice.builder(INVOICE_ID, "factura-gas.pdf").fields(gasFields).build();
        when(invoiceContextPort.loadInvoice(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());
        when(marketRatesContextPort.loadLatestRates(SupplyDomain.GAS)).thenReturn(List.of());

        assembler.assemble(INVOICE_ID, QUESTION);

        verify(marketRatesContextPort).loadLatestRates(SupplyDomain.GAS);
    }

    @Test
    void shouldNotLoadMarketRatesWhenNoInvoiceId() {
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());

        ChatContext context = assembler.assemble(null, QUESTION);

        assertThat(context.marketRates()).isEmpty();
        verify(marketRatesContextPort, never()).loadLatestRates(any());
    }

    @Test
    void shouldNotLoadMarketRatesWhenInvoiceNotFound() {
        when(invoiceContextPort.loadInvoice(INVOICE_ID)).thenReturn(Optional.empty());
        when(regulationSearchPort.search(any(), eq(MAX_RESULTS))).thenReturn(List.of());

        ChatContext context = assembler.assemble(INVOICE_ID, QUESTION);

        assertThat(context.marketRates()).isEmpty();
        verify(marketRatesContextPort, never()).loadLatestRates(any());
    }
}
package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind._shared.domain.event.DomainEventPublisher;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceIngested;
import dev.izquierdo.billmind.invoice.domain.event.InvoiceRejected;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.exceptions.UnsupportedSupplyTypeException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import dev.izquierdo.billmind.invoice.domain.port.PiiRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadInvoiceUseCaseTest {

    @Mock private InvoiceClassifier     invoiceClassifier;
    @Mock private InvoiceParser         invoiceParser;
    @Mock private PiiRedactor           piiRedactor;
    @Mock private InvoiceFieldExtractor fieldExtractor;
    @Mock private InvoiceRepository     invoiceRepository;
    @Mock private DomainEventPublisher  eventPublisher;

    @InjectMocks
    private UploadInvoiceUseCase uploadInvoiceUseCase;

    private Invoice invoice;
    private byte[] pdfContent;

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END   = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        invoice    = Invoice.builder(UUID.randomUUID(), "factura_enero.pdf").build();
        pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46};
    }

    @Test
    void shouldSaveEnrichedInvoiceWhenValidSupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.ELECTRICITY, "IBERDROLA"));
        when(piiRedactor.redact(anyString())).thenReturn("texto redactado");
        when(fieldExtractor.extract(anyString(), any()))
                .thenReturn(new ElectricityFields(START, END, new BigDecimal("45.50"), null, null, null, null, null, null, null, null, null, null, null));

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void shouldThrowWhenNotASupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSupplyTypeIsGas() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura gas");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.GAS, "NATURGY"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(UnsupportedSupplyTypeException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSupplyTypeIsAgua() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura agua");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.WATER, "AGUAS DE MADRID"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(UnsupportedSupplyTypeException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSupplyTypeIsTelco() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura telco");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.TELECOM, "MOVISTAR"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(UnsupportedSupplyTypeException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldNotCallFieldExtractorWhenNotASupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(fieldExtractor, never()).extract(anyString(), any());
    }

    @Test
    void shouldNotCallFieldExtractorWhenSupplyTypeNotSupported() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura gas");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.GAS, "NATURGY"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(UnsupportedSupplyTypeException.class);

        verify(fieldExtractor, never()).extract(anyString(), any());
    }

    @Test
    void shouldPublishInvoiceIngestedWhenValidSupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.ELECTRICITY, "IBERDROLA"));
        when(piiRedactor.redact(anyString())).thenReturn("texto redactado");
        when(fieldExtractor.extract(anyString(), any()))
                .thenReturn(new ElectricityFields(START, END, new BigDecimal("45.50"), null, null, null, null, null, null, null, null, null, null, null));

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        ArgumentCaptor<InvoiceIngested> captor = ArgumentCaptor.forClass(InvoiceIngested.class);
        verify(eventPublisher).publish(captor.capture());
        InvoiceIngested.Payload payload = captor.getValue().getData();
        assertThat(payload.invoiceId()).isEqualTo(invoice.getId());
        assertThat(payload.supplyType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(payload.provider()).isEqualTo("IBERDROLA");
    }

    @Test
    void shouldPublishInvoiceRejectedWhenNotASupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        ArgumentCaptor<InvoiceRejected> captor = ArgumentCaptor.forClass(InvoiceRejected.class);
        verify(eventPublisher).publish(captor.capture());
        InvoiceRejected.Payload payload = captor.getValue().getData();
        assertThat(payload.reason()).isEqualTo(InvoiceRejected.Reason.NOT_A_SUPPLY_INVOICE);
        assertThat(payload.detectedType()).isEqualTo(SupplyDomain.OTHER);
        assertThat(payload.invoiceId()).isEqualTo(invoice.getId());
    }

    @Test
    void shouldPublishInvoiceRejectedWhenSupplyTypeNotSupported() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura gas");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.GAS, "NATURGY"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(UnsupportedSupplyTypeException.class);

        ArgumentCaptor<InvoiceRejected> captor = ArgumentCaptor.forClass(InvoiceRejected.class);
        verify(eventPublisher).publish(captor.capture());
        InvoiceRejected.Payload payload = captor.getValue().getData();
        assertThat(payload.reason()).isEqualTo(InvoiceRejected.Reason.UNSUPPORTED_SUPPLY_TYPE);
        assertThat(payload.detectedType()).isEqualTo(SupplyDomain.GAS);
    }

    @Test
    void shouldNotPublishIngestedWhenRejected() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(SupplyDomain.OTHER, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(eventPublisher, never()).publish(any(InvoiceIngested.class));
    }
}
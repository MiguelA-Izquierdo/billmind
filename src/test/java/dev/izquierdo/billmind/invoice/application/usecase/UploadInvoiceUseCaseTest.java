package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.invoice.domain.model.fields.GasFields;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import dev.izquierdo.billmind.invoice.domain.port.PiiRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
                .thenReturn(new InvoiceClassification(InvoiceType.LUZ, "IBERDROLA"));
        when(piiRedactor.redact(anyString())).thenReturn("texto redactado");
        when(fieldExtractor.extract(anyString(), any()))
                .thenReturn(new ElectricityFields(START, END, new BigDecimal("45.50"), null, null, null));

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void shouldThrowWhenNotASupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(InvoiceType.OTRO, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldPersistSupplyTypeAndProviderFromClassification() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("texto factura");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(InvoiceType.GAS, "NATURGY"));
        when(piiRedactor.redact(anyString())).thenReturn("texto redactado");
        when(fieldExtractor.extract(anyString(), any()))
                .thenReturn(new GasFields(START, END, new BigDecimal("32.10"), null, null, null));

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void shouldNotCallFieldExtractorWhenNotASupplyInvoice() {
        when(invoiceParser.extractText(pdfContent)).thenReturn("contrato de arrendamiento");
        when(invoiceClassifier.classify(anyString()))
                .thenReturn(new InvoiceClassification(InvoiceType.OTRO, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(fieldExtractor, never()).extract(anyString(), any());
    }
}
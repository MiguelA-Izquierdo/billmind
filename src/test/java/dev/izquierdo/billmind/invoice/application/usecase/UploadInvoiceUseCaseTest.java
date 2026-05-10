package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceChunk;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceReference;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceChunkRepository;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceClassifier;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadInvoiceUseCaseTest {

    @Mock
    private InvoiceClassifier invoiceClassifier;

    @Mock
    private InvoiceParser invoiceParser;

    @Mock
    private InvoiceChunkRepository invoiceChunkRepository;

    @InjectMocks
    private UploadInvoiceUseCase uploadInvoiceUseCase;

    private Invoice invoice;
    private byte[] pdfContent;
    private List<InvoiceChunk> chunks;

    @BeforeEach
    void setUp() {
        UUID invoiceId = UUID.randomUUID();
        invoice = new Invoice(invoiceId, "factura_enero.pdf");
        pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46};
        InvoiceReference reference = new InvoiceReference(invoiceId, 1, "Invoice PDF Section");
        chunks = List.of(new InvoiceChunk("Importe total: 85,40€", reference));
    }

    @Test
    void shouldParseAndStoreChunksWhenValidSupplyInvoice() {
        when(invoiceClassifier.classify(pdfContent))
                .thenReturn(new InvoiceClassification(InvoiceType.LUZ, "IBERDROLA"));
        when(invoiceParser.parseToChunks(any(Invoice.class), any())).thenReturn(chunks);

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        verify(invoiceParser).parseToChunks(any(), any());
        verify(invoiceChunkRepository).store(chunks);
    }

    @Test
    void shouldThrowWhenNotASupplyInvoice() {
        when(invoiceClassifier.classify(pdfContent))
                .thenReturn(new InvoiceClassification(InvoiceType.OTRO, "MERCADONA"));

        assertThatThrownBy(() -> uploadInvoiceUseCase.upload(invoice, pdfContent))
                .isInstanceOf(NotASupplyInvoiceException.class);

        verify(invoiceParser, never()).parseToChunks(any(), any());
        verify(invoiceChunkRepository, never()).store(any());
    }

    @Test
    void shouldStoreEmptyListWhenParserReturnsNothing() {
        when(invoiceClassifier.classify(pdfContent))
                .thenReturn(new InvoiceClassification(InvoiceType.GAS, "NATURGY"));
        when(invoiceParser.parseToChunks(any(Invoice.class), any())).thenReturn(List.of());

        uploadInvoiceUseCase.upload(invoice, pdfContent);

        verify(invoiceChunkRepository).store(List.of());
    }
}

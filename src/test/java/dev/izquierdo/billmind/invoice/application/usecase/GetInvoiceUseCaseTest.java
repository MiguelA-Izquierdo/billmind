package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceNotFoundException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInvoiceUseCaseTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private GetInvoiceUseCase getInvoiceUseCase;

    @Test
    void shouldReturnInvoiceWhenFoundAndSessionMatches() {
        UUID invoiceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Invoice invoice = Invoice.builder(invoiceId, "factura.pdf").sessionId(sessionId).build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        Invoice result = getInvoiceUseCase.execute(invoiceId, sessionId);

        assertThat(result.getId()).isEqualTo(invoiceId);
        assertThat(result.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    void shouldThrowWhenInvoiceNotFound() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getInvoiceUseCase.execute(invoiceId, UUID.randomUUID()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenSessionDoesNotMatch() {
        UUID invoiceId = UUID.randomUUID();
        UUID ownerSession = UUID.randomUUID();
        Invoice invoice = Invoice.builder(invoiceId, "factura.pdf").sessionId(ownerSession).build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> getInvoiceUseCase.execute(invoiceId, UUID.randomUUID()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }
}
package dev.izquierdo.billmind.invoice.application.usecase;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSessionInvoicesUseCaseTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private GetSessionInvoicesUseCase getSessionInvoicesUseCase;

    @Test
    void shouldReturnInvoicesForSession() {
        UUID sessionId = UUID.randomUUID();
        List<Invoice> invoices = List.of(
                Invoice.builder(UUID.randomUUID(), "factura_enero.pdf").sessionId(sessionId).build(),
                Invoice.builder(UUID.randomUUID(), "factura_febrero.pdf").sessionId(sessionId).build()
        );
        when(invoiceRepository.findBySessionId(sessionId)).thenReturn(invoices);

        List<Invoice> result = getSessionInvoicesUseCase.execute(sessionId);

        assertThat(result).hasSize(2);
        assertThat(result).isSameAs(invoices);
    }

    @Test
    void shouldReturnEmptyListWhenNoInvoicesForSession() {
        UUID sessionId = UUID.randomUUID();
        when(invoiceRepository.findBySessionId(sessionId)).thenReturn(List.of());

        List<Invoice> result = getSessionInvoicesUseCase.execute(sessionId);

        assertThat(result).isEmpty();
    }
}
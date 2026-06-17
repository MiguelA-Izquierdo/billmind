package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaInvoiceRepositoryTest {

    @Mock
    private InvoiceJpaRepository jpa;

    @InjectMocks
    private JpaInvoiceRepository repository;

    @Test
    void shouldDelegateToJpaOnSave() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf")
                .supplyType(SupplyDomain.ELECTRICITY)
                .provider("IBERDROLA")
                .sessionId(UUID.randomUUID())
                .build();

        repository.save(invoice);

        verify(jpa).save(any(InvoiceEntity.class));
    }

    @Test
    void shouldReturnMappedDomainObjectWhenFound() {
        UUID id = UUID.randomUUID();
        Invoice original = Invoice.builder(id, "factura.pdf")
                .supplyType(SupplyDomain.GAS)
                .provider("NATURGY")
                .sessionId(UUID.randomUUID())
                .build();
        when(jpa.findById(id)).thenReturn(Optional.of(InvoiceEntity.from(original)));

        Optional<Invoice> result = repository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getSupplyType()).isEqualTo(SupplyDomain.GAS);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(jpa.findById(any())).thenReturn(Optional.empty());

        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldReturnMappedListForSession() {
        UUID sessionId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<InvoiceEntity> entities = List.of(
                InvoiceEntity.from(Invoice.builder(id1, "f1.pdf").sessionId(sessionId).supplyType(SupplyDomain.ELECTRICITY).provider("A").build()),
                InvoiceEntity.from(Invoice.builder(id2, "f2.pdf").sessionId(sessionId).supplyType(SupplyDomain.WATER).provider("B").build())
        );
        when(jpa.findBySessionId(sessionId)).thenReturn(entities);

        List<Invoice> result = repository.findBySessionId(sessionId);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Invoice::getId)).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void shouldReturnEmptyListWhenNoInvoicesForSession() {
        UUID sessionId = UUID.randomUUID();
        when(jpa.findBySessionId(sessionId)).thenReturn(List.of());

        assertThat(repository.findBySessionId(sessionId)).isEmpty();
    }
}
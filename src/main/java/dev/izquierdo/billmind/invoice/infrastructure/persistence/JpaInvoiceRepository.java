package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaInvoiceRepository implements InvoiceRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaInvoiceRepository.class);

    private final InvoiceJpaRepository jpa;

    public JpaInvoiceRepository(InvoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(Invoice invoice) {
        jpa.save(InvoiceEntity.from(invoice));
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpa.findById(id).map(InvoiceEntity::toDomain);
    }

    @Override
    public List<Invoice> findBySessionId(UUID sessionId) {
        return jpa.findBySessionId(sessionId).stream()
                .map(InvoiceEntity::toDomain)
                .toList();
    }
}
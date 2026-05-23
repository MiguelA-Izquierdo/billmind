package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaElectricityRateRepositoryTest {

    @Mock
    private ElectricityRateJpaRepository jpa;

    @InjectMocks
    private JpaElectricityRateRepository repository;

    @Test
    void shouldDelegateToJpaOnSave() {
        ElectricityRate rate = buildRate(UUID.randomUUID());

        repository.save(rate);

        verify(jpa).save(any(ElectricityRateEntity.class));
    }

    @Test
    void shouldSwallowDuplicateWhenDataIntegrityViolationThrown() {
        ElectricityRate rate = buildRate(UUID.randomUUID());
        doThrow(DataIntegrityViolationException.class).when(jpa).save(any(ElectricityRateEntity.class));

        assertDoesNotThrow(() -> repository.save(rate));
        verify(jpa).save(any(ElectricityRateEntity.class));
    }

    private ElectricityRate buildRate(UUID id) {
        return ElectricityRate.builder(id)
            .supplyType(InvoiceType.LUZ)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.150000"))
            .validFrom(LocalDate.of(2025, 1, 1))
            .source("REE")
            .build();
    }
}
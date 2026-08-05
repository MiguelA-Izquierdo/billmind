package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldCollapseDuplicateRowsToOnePerCompanyAndTariff() {
        // Same (company, tariffName) ingested three times — e.g. the same Kafka event replayed.
        ElectricityRateEntity dup1  = ElectricityRateMapper.toEntity(buildRate(UUID.randomUUID()));
        ElectricityRateEntity dup2  = ElectricityRateMapper.toEntity(buildRate(UUID.randomUUID()));
        ElectricityRateEntity dup3  = ElectricityRateMapper.toEntity(buildRate(UUID.randomUUID()));
        ElectricityRateEntity other = ElectricityRateMapper.toEntity(
            ElectricityRate.builder(UUID.randomUUID())
                .supplyType(SupplyDomain.ELECTRICITY)
                .company("ENDESA")
                .tariffName("Conecta Luz")
                .pricePerKwh(new BigDecimal("0.109000"))
                .validFrom(LocalDate.of(2025, 1, 1))
                .source("REE")
                .build());
        when(jpa.findLatestPerTariff(any(LocalDate.class))).thenReturn(List.of(dup1, dup2, dup3, other));

        List<ElectricityRate> result = repository.findLatestPerTariff();

        assertEquals(2, result.size());
    }

    @Test
    void shouldQueryExpiryWithTodaysDate() {
        when(jpa.findLatestPerTariff(any(LocalDate.class))).thenReturn(List.of());

        repository.findLatestPerTariff();

        verify(jpa).findLatestPerTariff(LocalDate.now());
    }

    private ElectricityRate buildRate(UUID id) {
        return ElectricityRate.builder(id)
            .supplyType(SupplyDomain.ELECTRICITY)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.150000"))
            .validFrom(LocalDate.of(2025, 1, 1))
            .source("REE")
            .build();
    }
}
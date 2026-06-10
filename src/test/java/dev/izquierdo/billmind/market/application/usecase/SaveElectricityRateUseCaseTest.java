package dev.izquierdo.billmind.market.application.usecase;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaveElectricityRateUseCaseTest {

    @Mock
    private ElectricityRateRepository repository;

    @InjectMocks
    private SaveElectricityRateUseCase useCase;

    @Test
    void shouldDelegateToRepository() {
        ElectricityRate rate = ElectricityRate.builder(UUID.randomUUID())
            .supplyType(InvoiceType.LUZ)
            .company("IBERDROLA")
            .tariffName("2.0TD")
            .pricePerKwh(new BigDecimal("0.150000"))
            .validFrom(LocalDate.of(2025, 1, 1))
            .source("REE")
            .build();

        useCase.execute(rate);

        verify(repository).save(rate);
    }
}
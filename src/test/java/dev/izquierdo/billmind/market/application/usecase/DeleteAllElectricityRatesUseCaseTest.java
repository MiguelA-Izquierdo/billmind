package dev.izquierdo.billmind.market.application.usecase;

import dev.izquierdo.billmind.market.domain.port.ElectricityRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteAllElectricityRatesUseCaseTest {

    @Mock
    private ElectricityRateRepository repository;

    @InjectMocks
    private DeleteAllElectricityRatesUseCase useCase;

    @Test
    void shouldDelegateToRepository() {
        useCase.execute();

        verify(repository).deleteAll();
    }

    @Test
    void shouldRejectNullRepository() {
        assertThrows(NullPointerException.class, () -> new DeleteAllElectricityRatesUseCase(null));
    }
}
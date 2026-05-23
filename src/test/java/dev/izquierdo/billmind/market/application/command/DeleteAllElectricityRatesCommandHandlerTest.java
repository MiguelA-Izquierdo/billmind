package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind.market.application.usecase.DeleteAllElectricityRatesUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteAllElectricityRatesCommandHandlerTest {

    @Mock
    private DeleteAllElectricityRatesUseCase useCase;

    @InjectMocks
    private DeleteAllElectricityRatesCommandHandler handler;

    @Test
    void shouldReturnCorrectCommandType() {
        assertEquals(DeleteAllElectricityRatesCommand.class, handler.commandType());
    }

    @Test
    void shouldDelegateToUseCase() {
        handler.handle(new DeleteAllElectricityRatesCommand());

        verify(useCase).execute();
    }

    @Test
    void shouldRejectNullUseCase() {
        assertThrows(NullPointerException.class, () -> new DeleteAllElectricityRatesCommandHandler(null));
    }
}
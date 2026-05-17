package dev.izquierdo.billmind._shared.infrastructure.command;

import dev.izquierdo.billmind._shared.application.command.Command;
import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SimpleCommandBusTest {

    record TestCommand() implements Command {}
    record OtherCommand() implements Command {}

    @SuppressWarnings("unchecked")
    private CommandHandler<TestCommand> handlerFor(Class<TestCommand> type) {
        CommandHandler<TestCommand> handler = mock(CommandHandler.class);
        when(handler.commandType()).thenReturn(type);
        return handler;
    }

    @Test
    void shouldDispatchToCorrectHandler() {
        CommandHandler<TestCommand> handler = handlerFor(TestCommand.class);
        SimpleCommandBus bus = new SimpleCommandBus(List.of(handler));
        TestCommand command = new TestCommand();

        bus.dispatch(command);

        verify(handler).handle(command);
    }

    @Test
    void shouldThrowWhenNoHandlerRegistered() {
        SimpleCommandBus bus = new SimpleCommandBus(List.of());

        assertThatThrownBy(() -> bus.dispatch(new TestCommand()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TestCommand");
    }

    @Test
    void shouldNotDispatchToWrongHandler() {
        CommandHandler<TestCommand> handler = handlerFor(TestCommand.class);
        SimpleCommandBus bus = new SimpleCommandBus(List.of(handler));

        assertThatThrownBy(() -> bus.dispatch(new OtherCommand()))
            .isInstanceOf(IllegalStateException.class);

        verify(handler, never()).handle(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchToCorrectHandlerAmongMultiple() {
        CommandHandler<TestCommand> testHandler = handlerFor(TestCommand.class);
        CommandHandler<OtherCommand> otherHandler = mock(CommandHandler.class);
        when(otherHandler.commandType()).thenReturn(OtherCommand.class);

        SimpleCommandBus bus = new SimpleCommandBus(List.of(testHandler, otherHandler));
        TestCommand command = new TestCommand();

        bus.dispatch(command);

        verify(testHandler).handle(command);
        verify(otherHandler, never()).handle(any());
    }
}
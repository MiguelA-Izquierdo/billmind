package dev.izquierdo.billmind._shared.infrastructure.command;

import dev.izquierdo.billmind._shared.application.command.Command;
import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SimpleCommandBus implements CommandBus {

    private static final Logger log = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final Map<Class<?>, CommandHandler<?>> handlers;

    public SimpleCommandBus(List<CommandHandler<?>> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(CommandHandler::commandType, h -> h));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatch(C command) {
        CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for command: " + command.getClass().getSimpleName());
        }
        log.debug("Dispatching command: {}", command.getClass().getSimpleName());
        handler.handle(command);
    }
}

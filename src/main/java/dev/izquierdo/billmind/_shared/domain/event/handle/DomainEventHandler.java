package dev.izquierdo.billmind._shared.domain.event.handle;

import dev.izquierdo.billmind._shared.domain.event.DomainEvent;

public interface DomainEventHandler<T extends DomainEvent<?>> {
    void handle(T event);

    Class<T> supportsEventType();
}
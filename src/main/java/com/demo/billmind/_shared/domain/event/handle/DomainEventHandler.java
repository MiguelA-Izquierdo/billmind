package com.demo.billmind._shared.domain.event.handle;

import com.demo.billmind._shared.domain.event.DomainEvent;

public interface DomainEventHandler<T extends DomainEvent<?>> {
    void handle(T event);

    Class<T> supportsEventType();
}
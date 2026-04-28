package com.demo.billmind._shared.domain.event;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseDomainEvent<T> implements DomainEvent<T> {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredOn = Instant.now();
    private final T data;


    public BaseDomainEvent(T data) {
        this.data = data;
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }

    @Override
    public T getData() {
        return data;
    }

    public abstract String getLogMessage();
}

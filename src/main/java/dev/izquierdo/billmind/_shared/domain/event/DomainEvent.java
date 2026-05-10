package dev.izquierdo.billmind._shared.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent<T> {
    UUID getEventId();

    Instant occurredOn();

    String eventName();

    T getData();
}

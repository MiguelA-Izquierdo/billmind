package dev.izquierdo.billmind._shared.domain.event;

public interface DomainEventPublisher {
    void publish(DomainEvent<?> event);
}

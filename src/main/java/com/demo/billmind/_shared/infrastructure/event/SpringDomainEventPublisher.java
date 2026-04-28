package com.demo.billmind._shared.infrastructure.event;

import com.demo.billmind._shared.domain.event.DomainEvent;
import com.demo.billmind._shared.domain.event.DomainEventPublisher;
import com.demo.billmind._shared.domain.event.handle.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(SpringDomainEventPublisher.class);


    private final Map<Class<? extends DomainEvent<?>>, List<DomainEventHandler<? extends DomainEvent<?>>>> handlers = new HashMap<>();

    public SpringDomainEventPublisher(List<DomainEventHandler<? extends DomainEvent<?>>> handlerBeans) {
        for (DomainEventHandler<? extends DomainEvent<?>> handler : handlerBeans) {
            handlers.computeIfAbsent(handler.supportsEventType(), k -> new ArrayList<>()).add(handler);
        }
    }

    @SuppressWarnings("unchecked")
    public void publish(DomainEvent<?> event) {
        List<DomainEventHandler<? extends DomainEvent<?>>> eventHandlers =
                handlers.getOrDefault(event.getClass(), Collections.emptyList());

        for (DomainEventHandler<? extends DomainEvent<?>> handler : eventHandlers) {
            DomainEventHandler<DomainEvent<?>> typedHandler = (DomainEventHandler<DomainEvent<?>>) handler;
            typedHandler.handle(event);
        }
    }

}

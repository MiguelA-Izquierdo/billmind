package dev.izquierdo.billmind._shared.infrastructure.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SimpleQueryBus implements QueryBus {

    private static final Logger log = LoggerFactory.getLogger(SimpleQueryBus.class);

    private final Map<Class<?>, QueryHandler<?, ?>> handlers;

    public SimpleQueryBus(List<QueryHandler<?, ?>> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(QueryHandler::queryType, h -> h));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, Q extends Query<R>> R dispatch(Q query) {
        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for query: " + query.getClass().getSimpleName());
        }
        log.debug("Dispatching query: {}", query.getClass().getSimpleName());
        return handler.handle(query);
    }
}
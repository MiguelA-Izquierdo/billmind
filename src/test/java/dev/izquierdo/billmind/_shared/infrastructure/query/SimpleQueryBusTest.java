package dev.izquierdo.billmind._shared.infrastructure.query;

import dev.izquierdo.billmind._shared.application.query.Query;
import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SimpleQueryBusTest {

    record TestQuery(String value) implements Query<String> {}
    record OtherQuery() implements Query<Integer> {}

    @SuppressWarnings("unchecked")
    private QueryHandler<TestQuery, String> handlerFor(Class<TestQuery> type) {
        QueryHandler<TestQuery, String> handler = mock(QueryHandler.class);
        when(handler.queryType()).thenReturn(type);
        return handler;
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchToCorrectHandlerAndReturnResult() {
        QueryHandler<TestQuery, String> handler = handlerFor(TestQuery.class);
        TestQuery query = new TestQuery("hello");
        when(handler.handle(query)).thenReturn("result");
        SimpleQueryBus bus = new SimpleQueryBus(List.of(handler));

        String result = bus.dispatch(query);

        assertThat(result).isEqualTo("result");
        verify(handler).handle(query);
    }

    @Test
    void shouldThrowWhenNoHandlerRegistered() {
        SimpleQueryBus bus = new SimpleQueryBus(List.of());

        assertThatThrownBy(() -> bus.dispatch(new TestQuery("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TestQuery");
    }

    @Test
    void shouldThrowWhenDispatchingUnregisteredQueryType() {
        QueryHandler<TestQuery, String> handler = handlerFor(TestQuery.class);
        SimpleQueryBus bus = new SimpleQueryBus(List.of(handler));

        assertThatThrownBy(() -> bus.dispatch(new OtherQuery()))
                .isInstanceOf(IllegalStateException.class);

        verify(handler, never()).handle(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchToCorrectHandlerAmongMultiple() {
        QueryHandler<TestQuery, String> testHandler = handlerFor(TestQuery.class);
        QueryHandler<OtherQuery, Integer> otherHandler = mock(QueryHandler.class);
        when(otherHandler.queryType()).thenReturn(OtherQuery.class);
        when(otherHandler.handle(any())).thenReturn(42);

        SimpleQueryBus bus = new SimpleQueryBus(List.of(testHandler, otherHandler));
        TestQuery query = new TestQuery("x");
        when(testHandler.handle(query)).thenReturn("ok");

        String result = bus.dispatch(query);

        assertThat(result).isEqualTo("ok");
        verify(testHandler).handle(query);
        verify(otherHandler, never()).handle(any());
    }
}
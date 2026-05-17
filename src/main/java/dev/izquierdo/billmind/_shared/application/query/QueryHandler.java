package dev.izquierdo.billmind._shared.application.query;

public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
    Class<Q> queryType();
}
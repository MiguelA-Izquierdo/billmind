package dev.izquierdo.billmind._shared.application.query;

public interface QueryBus {
    <R, Q extends Query<R>> R dispatch(Q query);
}
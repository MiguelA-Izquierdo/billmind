package dev.izquierdo.billmind._shared.infrastructure.route;

/**
 * How a request route is guarded. Authentication and session correlation are independent axes,
 * so each constant fixes both: a route never needs a bearer token and a session at the same time.
 */
public enum RouteAccess {

    /** No bearer token, no {@code X-Session-Id}. Static assets, actuator, public reads. */
    OPEN,

    /** No bearer token, {@code X-Session-Id} required. The anonymous visitor's own resources. */
    ANONYMOUS,

    /** Bearer token required, no {@code X-Session-Id}. Operations on shared, non-session data. */
    ADMIN
}
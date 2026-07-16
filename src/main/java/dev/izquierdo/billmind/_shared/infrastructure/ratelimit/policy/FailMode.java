package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

/**
 * What the limiter does when the backing store cannot be consulted (e.g. Redis down, timeout).
 * See {@code docs/RATELIMIT.md} for the per-profile rationale.
 */
public enum FailMode {

    /** Allow the request. Availability wins — used for cheap routes with no cost exposure. */
    FAIL_OPEN,

    /** Deny the request (HTTP 503, not 429). The cost/security property must survive a store outage. */
    FAIL_CLOSED
}
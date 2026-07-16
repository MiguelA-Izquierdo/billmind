package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

/**
 * When a limit can be enforced relative to authentication. A key derived from a validated bearer
 * token is only trustworthy once {@code JwtAuthFilter} has run, so it belongs to a separate,
 * post-authentication checkpoint from IP- and session-derived keys.
 */
public enum RateLimitPhase {

    /** Enforced before token validation. Keys derived from IP or the session header. */
    PRE_AUTH,

    /** Enforced after token validation. Keys derived from the validated bearer token. */
    POST_AUTH
}
package dev.izquierdo.billmind._shared.infrastructure.auth;

/**
 * The authorities this application grants. Spring Security's {@code hasRole('ADMIN')} matches the
 * {@code ROLE_}-prefixed authority, so the prefix lives here once instead of at every call site.
 */
public final class Roles {

    /** Granted by {@link JwtAuthFilter} to a bearer token whose introspection reports an admin role. */
    public static final String ADMIN = "ROLE_ADMIN";

    private Roles() {
    }
}
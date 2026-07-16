package dev.izquierdo.billmind._shared.infrastructure.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The identity {@link JwtAuthFilter} establishes from a bearer token, once the external introspection
 * endpoint has ruled on it. The principal is a SHA-256 fingerprint of the token, never the token itself
 * (rule #6), so downstream code can key on a per-credential identity without ever seeing the credential.
 *
 * <p>A rejected token is <strong>not authenticated</strong> — {@code isAuthenticated()} is {@code false}
 * and there are no authorities. Marking it authenticated (as an earlier revision did) would have been a
 * lie with teeth: every authority-based check in Spring Security gates on {@code isAuthenticated()}
 * first ({@code AuthorityAuthorizationManager}, {@code AuthenticatedAuthorizationManager}), so the day
 * someone wrote {@code authenticated()} or {@code @PreAuthorize("isAuthenticated()")} on a route, a
 * token the identity provider had explicitly refused would have passed it.
 *
 * <p>The {@code 401}/{@code 403} split does <em>not</em> rest on that flag and never did. It rests on
 * the <em>type</em> of the token in the context: {@code ExceptionTranslationFilter} answers {@code 401}
 * only when the authentication is anonymous or remember-me. No token at all leaves the context to
 * {@code AnonymousAuthenticationFilter} → {@code 401}; a refused token leaves this object there — not
 * anonymous, so a denial renders as {@code 403}. {@code AdminRouteAuthorizationTest} pins both.
 */
public final class ExternalTokenAuthentication extends AbstractAuthenticationToken {

    private final String fingerprint;
    private final boolean admin;

    private ExternalTokenAuthentication(String fingerprint, boolean admin) {
        super(admin ? List.of(new SimpleGrantedAuthority(Roles.ADMIN)) : List.of());
        this.fingerprint = fingerprint;
        this.admin = admin;
        setAuthenticated(admin);
    }

    /** The introspection reported an admin role: an authenticated caller carrying {@link Roles#ADMIN}. */
    public static ExternalTokenAuthentication authorized(String rawToken) {
        return new ExternalTokenAuthentication(fingerprintOf(rawToken), true);
    }

    /**
     * The introspection refused the token, or accepted it without an admin role. Either way it grants
     * nothing here: the object exists only so the denial can be told apart from an anonymous request.
     */
    public static ExternalTokenAuthentication rejected(String rawToken) {
        return new ExternalTokenAuthentication(fingerprintOf(rawToken), false);
    }

    /** True only for a token introspection accepted — the one identity worth rate-limiting per credential. */
    public boolean isAdmin() {
        return admin;
    }

    @Override
    public Object getPrincipal() {
        return fingerprint;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    private static String fingerprintOf(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
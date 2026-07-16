package dev.izquierdo.billmind._shared.domain.port;

public interface ExternalAuthPort {

    /**
     * Whether the bearer token grants administrative access. A token that is merely <em>valid</em>
     * — an ordinary user's — is not authorized: the answer comes from the roles the identity provider
     * reports, never from the fact that it answered at all. Fail-closed on any error.
     */
    boolean isAuthorized(String bearerToken);
}
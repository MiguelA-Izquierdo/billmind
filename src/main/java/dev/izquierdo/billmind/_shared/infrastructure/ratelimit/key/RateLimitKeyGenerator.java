package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Derives the identity fragment a limit is counted against, for one {@link KeyType}. The caller
 * namespaces the returned fragment by profile to build the final bucket key, so the same IP or
 * session never shares a bucket across profiles.
 *
 * <p>Returns {@link Optional#empty()} when the request carries no such identity (e.g. no session
 * header): the caller then skips this layer rather than counting everyone under a shared blank key.
 */
public interface RateLimitKeyGenerator {

    KeyType type();

    Optional<String> identity(HttpServletRequest request);
}
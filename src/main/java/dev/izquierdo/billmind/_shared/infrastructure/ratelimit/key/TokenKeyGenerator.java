package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.auth.ExternalTokenAuthentication;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Keys by the fingerprint of the <em>validated</em> credential — the principal {@code JwtAuthFilter} put
 * in the {@code SecurityContext}, never the raw {@code Authorization} header. Reading the header instead
 * would let an anonymous caller mint a fresh bucket per forged token and grow the store without bound;
 * an identity that only exists once introspection has accepted the token makes that impossible by
 * construction. A caller without one simply has no token layer (the limiter skips it) and is stopped by
 * the authorization engine anyway.
 */
@Component
public class TokenKeyGenerator implements RateLimitKeyGenerator {

    @Override
    public KeyType type() {
        return KeyType.TOKEN;
    }

    @Override
    public Optional<String> identity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof ExternalTokenAuthentication token) || !token.isAdmin()) {
            return Optional.empty();
        }
        return Optional.of("tok:" + token.getPrincipal());
    }
}
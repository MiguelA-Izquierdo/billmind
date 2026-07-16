package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Keys by the {@code X-Session-Id} header — the anonymous visitor's own budget, and <em>only</em> that.
 * The header is written by the client and proves nothing: rotating it mints a fresh bucket, so this
 * layer bounds an honest visitor and no one else. What bounds an attacker is the {@link KeyType#IP}
 * ceiling that {@code UPLOAD} and {@code CHAT} carry underneath it. Never let this be the last line of
 * defence on a paid route.
 *
 * <p>The value is parsed and re-rendered in canonical form rather than used raw: this runs before
 * {@code SessionFilter}, and the same session spelled in upper case would otherwise get a second
 * bucket. A value that is not a UUID yields {@link Optional#empty()} — the layer is skipped, the IP
 * ceiling still applies, and {@code SessionFilter} rejects the request downstream anyway.
 */
@Component
public class SessionKeyGenerator implements RateLimitKeyGenerator {

    private static final String SESSION_HEADER = "X-Session-Id";

    @Override
    public KeyType type() {
        return KeyType.SESSION;
    }

    @Override
    public Optional<String> identity(HttpServletRequest request) {
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of("sess:" + UUID.fromString(header.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
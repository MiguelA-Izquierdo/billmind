package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Keys by client IP. Behind a reverse proxy the real client sits in {@code X-Forwarded-For}, but
 * that header is attacker-spoofable, so it is only honoured when {@code trustForwardedFor} is
 * enabled — which must only be true when every request reaches the app through a trusted proxy that
 * overwrites the header. Otherwise the direct {@code RemoteAddr} is used.
 */
@Component
public class IpKeyGenerator implements RateLimitKeyGenerator {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final boolean trustForwardedFor;

    public IpKeyGenerator(
            @Value("${billmind.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public KeyType type() {
        return KeyType.IP;
    }

    @Override
    public Optional<String> identity(HttpServletRequest request) {
        return Optional.of("ip:" + clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
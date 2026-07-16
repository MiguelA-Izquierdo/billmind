package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import org.springframework.stereotype.Component;

/**
 * The pre-authentication checkpoint, wired before {@code JwtAuthFilter}. Enforces the IP- and
 * session-keyed layers, so brute force against admin routes is capped before the token introspection
 * network call, and the expensive upload/chat routes are protected before any handler runs.
 */
@Component
public class RateLimitFilter extends AbstractRateLimitFilter {

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitPolicyResolver policyResolver,
                           ObjectMapper objectMapper) {
        super(rateLimiter, policyResolver, objectMapper);
    }

    @Override
    protected RateLimitPhase phase() {
        return RateLimitPhase.PRE_AUTH;
    }
}
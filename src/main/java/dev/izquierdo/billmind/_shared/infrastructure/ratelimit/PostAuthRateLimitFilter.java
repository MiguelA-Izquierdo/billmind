package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import org.springframework.stereotype.Component;

/**
 * The post-authentication checkpoint, wired after {@code JwtAuthFilter}. Enforces token-keyed layers,
 * so a single valid credential is capped even across IPs. Only ADMIN carries a post-auth layer today,
 * so {@link #shouldNotFilter} skips every other route.
 */
@Component
public class PostAuthRateLimitFilter extends AbstractRateLimitFilter {

    public PostAuthRateLimitFilter(RateLimiter rateLimiter, RateLimitPolicyResolver policyResolver,
                                   ObjectMapper objectMapper) {
        super(rateLimiter, policyResolver, objectMapper);
    }

    @Override
    protected RateLimitPhase phase() {
        return RateLimitPhase.POST_AUTH;
    }
}
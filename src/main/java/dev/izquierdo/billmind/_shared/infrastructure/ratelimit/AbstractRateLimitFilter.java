package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Shared machinery for the two rate-limit checkpoints. Each subclass fixes a {@link RateLimitPhase}
 * — pre-auth (IP/session) or post-auth (validated token) — and this class handles the servlet
 * concerns: skipping routes with no layer for the phase, emitting {@code X-RateLimit-*} headers, and
 * writing a Spanish {@code 429}/{@code 503} body directly (the filter runs before the DispatcherServlet,
 * like {@code SessionFilter}, so it cannot rely on {@code GlobalExceptionHandler}).
 */
public abstract class AbstractRateLimitFilter extends OncePerRequestFilter {

    private static final String UNAVAILABLE_MESSAGE =
            "El servicio no está disponible temporalmente. Inténtalo de nuevo en unos instantes.";

    private final RateLimiter rateLimiter;
    private final RateLimitPolicyResolver policyResolver;
    private final ObjectMapper objectMapper;

    protected AbstractRateLimitFilter(RateLimiter rateLimiter, RateLimitPolicyResolver policyResolver,
                                      ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.policyResolver = policyResolver;
        this.objectMapper = objectMapper;
    }

    protected abstract RateLimitPhase phase();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return policyResolver.profileFor(request).keyTypes().stream()
                .noneMatch(keyType -> keyType.phase() == phase());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitProfile profile = policyResolver.profileFor(request);
        RateLimitVerdict verdict = rateLimiter.check(request, profile, phase());
        applyHeaders(response, verdict);
        if (verdict.rejected()) {
            reject(response, verdict);
            return;
        }
        chain.doFilter(request, response);
    }

    /** {@code X-RateLimit-Reset} is seconds from now, not an epoch: no clock agreement needed. */
    private void applyHeaders(HttpServletResponse response, RateLimitVerdict verdict) {
        if (verdict.hasHeaders()) {
            response.setHeader("X-RateLimit-Limit", Long.toString(verdict.limit()));
            response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, verdict.remaining())));
            response.setHeader("X-RateLimit-Reset", Long.toString(ceilSeconds(verdict.resetAfter())));
        }
        if (verdict.rejected() && !verdict.retryAfter().isZero()) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, ceilSeconds(verdict.retryAfter()))));
        }
    }

    private void reject(HttpServletResponse response, RateLimitVerdict verdict) throws IOException {
        int status = verdict.httpStatus();
        // The bucket knows how long the wait is — say it, instead of a bare "try later".
        String message = status == 429
                ? ThrottleMessages.throttled(verdict.retryAfter())
                : UNAVAILABLE_MESSAGE;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponseDTO.of(status, message));
    }

    private long ceilSeconds(Duration duration) {
        return (long) Math.ceil(duration.toMillis() / 1000.0);
    }
}
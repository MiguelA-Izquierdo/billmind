package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccess;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the rate-limit profile of every route. Delegates the admin / open /
 * anonymous split to {@link RouteAccessPolicy} so the limiter's notion of an admin route can never
 * drift from the one the auth filters enforce, then refines within each access class. Path matching
 * goes through {@link RequestPathMatcher} for the same reason the route policy does: an encoded path
 * the router serves must not slip into a cheaper profile (e.g. {@code /invoice%73} → {@code DEFAULT}
 * instead of {@code UPLOAD}) because its raw URI was spelled differently.
 *
 * <p>Any unmapped route under {@code /api/v1/} falls to {@link RateLimitProfile#DEFAULT} — a new
 * endpoint is session-scoped and fail-closed until someone decides otherwise.
 */
@Component
public class RateLimitPolicyResolver {

    private static final String UPLOAD_PATH = "/api/v1/invoices";
    private static final String CHAT_PATH = "/api/v1/assistant/chat";
    private static final String INVOICE_READ_PATTERN = "/api/v1/invoices/**";

    private final RouteAccessPolicy routeAccessPolicy;
    private final RequestPathMatcher pathMatcher;

    public RateLimitPolicyResolver(RouteAccessPolicy routeAccessPolicy, RequestPathMatcher pathMatcher) {
        this.routeAccessPolicy = routeAccessPolicy;
        this.pathMatcher = pathMatcher;
    }

    public RateLimitProfile profileFor(HttpServletRequest request) {
        RouteAccess access = routeAccessPolicy.accessFor(request);
        return switch (access) {
            case ADMIN -> adminProfile(request);
            // Nothing under the API tree is OPEN: this is static assets and actuator.
            case OPEN -> RateLimitProfile.NONE;
            case ANONYMOUS -> anonymousProfile(request);
        };
    }

    /** Reads get their own budget; the tight one exists to guard the routes that change state. */
    private RateLimitProfile adminProfile(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) ? RateLimitProfile.ADMIN_READ : RateLimitProfile.ADMIN;
    }

    private RateLimitProfile anonymousProfile(HttpServletRequest request) {
        if (pathMatcher.matches("POST", UPLOAD_PATH, request)) {
            return RateLimitProfile.UPLOAD;
        }
        if (pathMatcher.matches("POST", CHAT_PATH, request)) {
            return RateLimitProfile.CHAT;
        }
        if (pathMatcher.matches("GET", INVOICE_READ_PATTERN, request)) {
            return RateLimitProfile.PUBLIC_READ;
        }
        return RateLimitProfile.DEFAULT;
    }
}
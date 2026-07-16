package dev.izquierdo.billmind._shared.infrastructure.route;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for how every route is guarded. {@code JwtAuthFilter} and
 * {@code SessionFilter} both consult it, so a route can never be registered with one filter
 * and forgotten by the other.
 *
 * <p>Matching goes through {@link RequestPathMatcher} — the decoded path the {@code DispatcherServlet}
 * routes on — never the raw {@code getRequestURI()}, so an encoded admin path (e.g. {@code /%61dmin/})
 * cannot slip past this guard as anonymous while the router still hands it to an admin controller.
 */
@Component
public class RouteAccessPolicy {

    /** Everything the API serves; anything outside it (static assets, actuator) is {@link RouteAccess#OPEN}. */
    private static final String API_TREE = "/api/v1/**";

    /**
     * Every route under this prefix is admin-only regardless of HTTP method, so a newly added
     * admin endpoint is guarded without having to be registered in {@link #ADMIN_ROUTES}.
     */
    private static final List<String> ADMIN_PATH_PREFIXES = List.of("/api/v1/admin/**");

    /** Admin routes living outside the {@code /api/v1/admin} tree, keyed by HTTP method. */
    private static final Map<String, List<String>> ADMIN_ROUTES = Map.of(
            "DELETE", List.of("/api/v1/market-rates")
    );

    /** API routes readable without a token and without a session, keyed by HTTP method. */
    private static final Map<String, List<String>> OPEN_ROUTES = Map.of(
            "GET", List.of("/api/v1/market-rates")
    );

    private final RequestPathMatcher pathMatcher;

    public RouteAccessPolicy(RequestPathMatcher pathMatcher) {
        this.pathMatcher = pathMatcher;
    }

    /**
     * Anything unrecognized under the API tree defaults to {@link RouteAccess#ANONYMOUS}:
     * a new endpoint is session-scoped until someone decides otherwise.
     */
    public RouteAccess accessFor(HttpServletRequest request) {
        if (!pathMatcher.matches(API_TREE, request)) {
            return RouteAccess.OPEN;
        }
        if (isAdmin(request)) {
            return RouteAccess.ADMIN;
        }
        if (matchesAny(OPEN_ROUTES, request)) {
            return RouteAccess.OPEN;
        }
        return RouteAccess.ANONYMOUS;
    }

    private boolean isAdmin(HttpServletRequest request) {
        boolean underAdminPrefix = ADMIN_PATH_PREFIXES.stream()
                .anyMatch(pattern -> pathMatcher.matches(pattern, request));
        return underAdminPrefix || matchesAny(ADMIN_ROUTES, request);
    }

    private boolean matchesAny(Map<String, List<String>> routes, HttpServletRequest request) {
        return routes.getOrDefault(request.getMethod(), List.of()).stream()
                .anyMatch(pattern -> pathMatcher.matches(pattern, request));
    }
}
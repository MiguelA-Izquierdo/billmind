package dev.izquierdo.billmind._shared.infrastructure.route;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for how every route is guarded. {@code JwtAuthFilter} and
 * {@code SessionFilter} both consult it, so a route can never be registered with one filter
 * and forgotten by the other.
 */
@Component
public class RouteAccessPolicy {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String API_PREFIX = "/api/v1/";

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

    /**
     * Anything unrecognized under {@value #API_PREFIX} defaults to {@link RouteAccess#ANONYMOUS}:
     * a new endpoint is session-scoped until someone decides otherwise.
     */
    public RouteAccess accessFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(API_PREFIX)) {
            return RouteAccess.OPEN;
        }
        String method = request.getMethod();
        if (isAdmin(method, uri)) {
            return RouteAccess.ADMIN;
        }
        if (matches(OPEN_ROUTES, method, uri)) {
            return RouteAccess.OPEN;
        }
        return RouteAccess.ANONYMOUS;
    }

    private boolean isAdmin(String method, String uri) {
        boolean underAdminPrefix = ADMIN_PATH_PREFIXES.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
        return underAdminPrefix || matches(ADMIN_ROUTES, method, uri);
    }

    private boolean matches(Map<String, List<String>> routes, String method, String uri) {
        return routes.getOrDefault(method, List.of()).stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
package dev.izquierdo.billmind._shared.infrastructure.route;

import dev.izquierdo.billmind._shared.infrastructure.auth.Roles;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Feeds {@link RouteAccessPolicy} to Spring Security's authorization engine, so the access decision is
 * taken by {@code AuthorizationFilter} — not by a filter's {@code shouldNotFilter}. {@code ADMIN} routes
 * require the {@link Roles#ADMIN} authority that {@code JwtAuthFilter} grants; {@code OPEN} and
 * {@code ANONYMOUS} routes are granted here and guarded further down by {@code SessionFilter}.
 *
 * <p>This is one of three independent layers: it catches wiring failures (a filter unregistered, the
 * chain reordered), while the {@code @PreAuthorize} on each admin handler catches a misclassification
 * by the policy itself — a bug this manager, which trusts the same policy, would wave through.
 */
@Component
public class RouteAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RouteAccessPolicy routeAccessPolicy;

    public RouteAccessAuthorizationManager(RouteAccessPolicy routeAccessPolicy) {
        this.routeAccessPolicy = routeAccessPolicy;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        if (routeAccessPolicy.accessFor(context.getRequest()) != RouteAccess.ADMIN) {
            return new AuthorizationDecision(true);
        }
        return new AuthorizationDecision(hasAdminAuthority(authentication.get()));
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.ADMIN::equals);
    }
}
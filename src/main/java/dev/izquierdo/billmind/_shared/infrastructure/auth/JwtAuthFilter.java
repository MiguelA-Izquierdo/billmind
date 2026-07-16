package dev.izquierdo.billmind._shared.infrastructure.auth;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccess;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates a bearer token against the external introspection endpoint and puts the resulting
 * identity in the {@code SecurityContext}. It never rejects: the access decision belongs to
 * {@code AuthorizationFilter} (via {@code RouteAccessAuthorizationManager}) and to the
 * {@code @PreAuthorize} on each admin handler, which is what keeps a single bug in this filter from
 * opening an admin route.
 *
 * <p>{@link #shouldNotFilter} consults {@link RouteAccessPolicy} only to <em>skip</em> work — a route
 * that needs no token, or a request carrying none, means no introspection call, which keeps an
 * anonymous caller from turning the public API into an amplifier against the auth service. Skipping is
 * safe in a way granting was not: a route the policy misclassifies is simply left unauthenticated, and
 * the layers downstream deny it.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ExternalAuthPort externalAuthPort;
    private final RouteAccessPolicy routeAccessPolicy;

    public JwtAuthFilter(ExternalAuthPort externalAuthPort, RouteAccessPolicy routeAccessPolicy) {
        this.externalAuthPort = externalAuthPort;
        this.routeAccessPolicy = routeAccessPolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return routeAccessPolicy.accessFor(request) != RouteAccess.ADMIN || bearerTokenOf(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        String token = bearerTokenOf(request);

        SecurityContextHolder.getContext().setAuthentication(
                externalAuthPort.isAuthorized(authorization)
                        ? ExternalTokenAuthentication.authorized(token)
                        : ExternalTokenAuthentication.rejected(token));

        chain.doFilter(request, response);
    }

    private String bearerTokenOf(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
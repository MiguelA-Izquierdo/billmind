package dev.izquierdo.billmind._shared.infrastructure.auth;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

/**
 * Runs on the internal management port (Actuator child context). When a request carries a
 * Bearer token that the external auth service authorizes — which only happens for admins,
 * since {@code /introspect} returns 2xx exclusively for admin tokens — the request is wrapped
 * so that {@code getUserPrincipal()} reports a non-null principal.
 *
 * <p>Actuator's {@code health.show-details=when-authorized} check reads that servlet method
 * (via its {@code SecurityContext}); a non-null principal is enough to expose full health
 * details. The filter never rejects a request, keeping liveness/readiness probes reachable
 * unauthenticated.
 */
public class ManagementHealthAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ExternalAuthPort externalAuthPort;

    public ManagementHealthAuthFilter(ExternalAuthPort externalAuthPort) {
        this.externalAuthPort = externalAuthPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpServletRequest effective = request;
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)
                && externalAuthPort.isAuthorized(authorization)) {
            effective = new AdminRequest(request);
        }
        chain.doFilter(effective, response);
    }

    private static final class AdminRequest extends HttpServletRequestWrapper {

        private static final Principal ADMIN_PRINCIPAL = () -> "actuator-admin";

        private AdminRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Principal getUserPrincipal() {
            return ADMIN_PRINCIPAL;
        }
    }
}
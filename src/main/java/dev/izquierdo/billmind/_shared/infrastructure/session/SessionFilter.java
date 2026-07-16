package dev.izquierdo.billmind._shared.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccess;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class SessionFilter extends OncePerRequestFilter {

    private static final String SESSION_HEADER = "X-Session-Id";

    private final SessionService sessionService;
    private final SessionContext sessionContext;
    private final ObjectMapper objectMapper;
    private final RouteAccessPolicy routeAccessPolicy;

    public SessionFilter(SessionService sessionService, SessionContext sessionContext,
                         ObjectMapper objectMapper, RouteAccessPolicy routeAccessPolicy) {
        this.sessionService = sessionService;
        this.sessionContext = sessionContext;
        this.objectMapper = objectMapper;
        this.routeAccessPolicy = routeAccessPolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return routeAccessPolicy.accessFor(request) != RouteAccess.ANONYMOUS;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            reject(response, "La cabecera X-Session-Id es obligatoria");
            return;
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(header.strip());
        } catch (IllegalArgumentException e) {
            reject(response, "La cabecera X-Session-Id debe ser un UUID válido");
            return;
        }
        if (createsState(request)) {
            sessionService.upsert(sessionId);
        }
        sessionContext.setSessionId(sessionId);
        chain.doFilter(request, response);
    }

    /**
     * A read never brings a session into existence. Upserting on every request turned a header the
     * client makes up into a database INSERT: an unauthenticated caller could grow the {@code sessions}
     * table one forged UUID at a time by hammering reads — including unmapped paths, which write a row
     * and then answer 404. Restricting the write to the requests that actually create session-scoped
     * state leaves the row count bounded by the rate limit of the routes that produce it.
     *
     * <p>Reading a session that was never written is not a problem: nothing joins against this table
     * ({@code invoices.session_id} is a plain column, no foreign key), so a visitor who only ever
     * looked around simply leaves no trace — which is the right answer.
     */
    private boolean createsState(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod());
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), message)
        );
    }
}
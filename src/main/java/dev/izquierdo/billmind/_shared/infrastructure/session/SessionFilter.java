package dev.izquierdo.billmind._shared.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final PublicRoutesService publicRoutesService;

    public SessionFilter(SessionService sessionService, SessionContext sessionContext,
                         ObjectMapper objectMapper, PublicRoutesService publicRoutesService) {
        this.sessionService = sessionService;
        this.sessionContext = sessionContext;
        this.objectMapper = objectMapper;
        this.publicRoutesService = publicRoutesService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/") || publicRoutesService.isPublicRoute(request);
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
            sessionId = UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            reject(response, "La cabecera X-Session-Id debe ser un UUID válido");
            return;
        }
        sessionService.upsert(sessionId);
        sessionContext.setSessionId(sessionId);
        chain.doFilter(request, response);
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
package dev.izquierdo.billmind._shared.infrastructure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders both authorization outcomes in the API's own envelope. Every denial in the application —
 * whether it comes from {@code AuthorizationFilter} or from a {@code @PreAuthorize} on a handler —
 * is translated by {@code ExceptionTranslationFilter} and lands here, so the wire format and the
 * Spanish wording live in one place.
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String UNAUTHENTICATED_MESSAGE = "Se requiere autenticación para realizar esta operación";
    private static final String FORBIDDEN_MESSAGE = "No tienes permisos para realizar esta operación";

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No credentials at all: the caller is anonymous and could still authenticate. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_MESSAGE);
    }

    /** Credentials present but insufficient — a rejected token, or a role the route does not accept. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponseDTO.of(status.value(), message));
    }
}
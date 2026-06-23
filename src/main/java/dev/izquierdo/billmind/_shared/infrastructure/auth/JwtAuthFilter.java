package dev.izquierdo.billmind._shared.infrastructure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
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

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final ExternalAuthPort externalAuthPort;
    private final AdminRoutesService adminRoutesService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(ExternalAuthPort externalAuthPort, AdminRoutesService adminRoutesService,
                         ObjectMapper objectMapper) {
        this.externalAuthPort = externalAuthPort;
        this.adminRoutesService = adminRoutesService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !adminRoutesService.isAdminRoute(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            reject(response, "Se requiere autenticación para realizar esta operación", HttpStatus.UNAUTHORIZED);
            return;
        }
        if (!externalAuthPort.isAuthorized(authorization)) {
            reject(response, "No tienes permisos para realizar esta operación", HttpStatus.FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponseDTO.of(status.value(), message));
    }
}
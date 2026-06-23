package dev.izquierdo.billmind._shared.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

@Component
public class AdminRoutesService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Map<String, List<String>> ADMIN_ROUTES = Map.of(
            "DELETE", List.of("/api/v1/market-rates")
    );

    public boolean isAdminRoute(HttpServletRequest request) {
        List<String> patterns = ADMIN_ROUTES.getOrDefault(request.getMethod(), List.of());
        String uri = request.getRequestURI();
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
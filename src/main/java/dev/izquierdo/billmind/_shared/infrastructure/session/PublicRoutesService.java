package dev.izquierdo.billmind._shared.infrastructure.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

@Component
public class PublicRoutesService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Map<String, List<String>> PUBLIC_ROUTES = Map.of(
            "GET",    List.of("/api/v1/market-rates"),
            "POST",   List.of(),
            "PUT",    List.of(),
            "PATCH",  List.of(),
            "DELETE", List.of("/api/v1/market-rates")
    );

    public boolean isPublicRoute(HttpServletRequest request) {
        List<String> patterns = PUBLIC_ROUTES.getOrDefault(request.getMethod(), List.of());
        String uri = request.getRequestURI();
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
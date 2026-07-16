package dev.izquierdo.billmind._shared.infrastructure.route;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches a request against a path pattern exactly as the {@code DispatcherServlet} does when it picks
 * a handler.
 *
 * <p>Matching on {@code request.getRequestURI()} — the raw, percent-encoded URI off the wire — does
 * not: {@code RequestPath} decodes each segment first, so {@code /api/v1/%61dmin/…} reaches its
 * controller while failing a raw match against {@code /api/v1/admin/**}. A guard that disagrees with
 * the router is one that can be walked around, so every route guard matches through here.
 */
@Component
public class RequestPathMatcher {

    private final Map<String, PathPattern> patterns = new ConcurrentHashMap<>();

    public boolean matches(String pattern, HttpServletRequest request) {
        return patterns.computeIfAbsent(pattern, PathPatternParser.defaultInstance::parse)
                .matches(pathOf(request));
    }

    public boolean matches(String method, String pattern, HttpServletRequest request) {
        return method.equals(request.getMethod()) && matches(pattern, request);
    }

    /** Security filters run before the dispatcher, so the parse may not have happened yet; it caches. */
    private PathContainer pathOf(HttpServletRequest request) {
        RequestPath path = ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : ServletRequestPathUtils.parseAndCache(request);
        return path.pathWithinApplication();
    }
}
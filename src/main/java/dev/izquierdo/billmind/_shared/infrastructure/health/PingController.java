package dev.izquierdo.billmind._shared.infrastructure.health;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness probe for uptime monitors and load balancers: {@code 200} when PostgreSQL — and
 * Kafka, when enabled — answer, {@code 503} when they do not. The body is always empty, in both
 * directions, so an anonymous caller learns whether the service can work and nothing beyond that:
 * no dependency names, no versions, no error text.
 *
 * <p>It sits outside {@code /api/v1/**} on purpose. Inside that tree {@code RouteAccessPolicy}
 * classifies every unrecognized route as {@code ANONYMOUS}, which makes {@code X-Session-Id}
 * mandatory — a header no monitor sends. Outside it the route is {@code OPEN}, which is exactly
 * what "public" means here, and the answer is cached by {@link DependencyHealthProbe} so being
 * unmetered costs nothing.
 */
@RestController
public class PingController {

    private final DependencyHealthProbe dependencyHealthProbe;

    public PingController(DependencyHealthProbe dependencyHealthProbe) {
        this.dependencyHealthProbe = dependencyHealthProbe;
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        HttpStatus status = dependencyHealthProbe.dependenciesUp()
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        // A cached 200 would keep reporting health long after the dependency went away.
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }
}
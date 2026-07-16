package dev.izquierdo.billmind._shared.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Asks the external auth service what a bearer token is worth. {@code GET /introspect} answers with
 * the token's subject and roles:
 *
 * <pre>{@code { "sub": "...", "roles": ["ROLE_USER", "ROLE_SUPER_ADMIN"], "isAdmin": true } }</pre>
 *
 * <p>The decision comes from {@code roles}, never from the status code: a 2xx only means the token is
 * <em>valid</em>, and the service issues valid tokens to ordinary users too. Granting on 2xx alone would
 * hand {@code ROLE_ADMIN} to every authenticated user. The {@code isAdmin} flag is deliberately ignored —
 * {@code roles} is the authority, and a derived flag is one more thing that can disagree with it.
 *
 * <p>Every other outcome (4xx, 5xx, unreachable, unparseable or role-less body) is not admin: fail-closed.
 */
@Component
public class ExternalAuthAdapter implements ExternalAuthPort {

    private static final Logger log = LoggerFactory.getLogger(ExternalAuthAdapter.class);

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private final RestClient restClient;
    private final String introspectUrl;

    public ExternalAuthAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.auth.external-url}") String externalUrl) {
        this.restClient = restClientBuilder.baseUrl(externalUrl).build();
        this.introspectUrl = externalUrl + "/introspect";
    }

    /** True only when the token carries an admin role — this is what grants {@code ROLE_ADMIN}. */
    @Override
    public boolean isAuthorized(String bearerToken) {
        log.debug("GET {} — token: {}", introspectUrl, mask(bearerToken));
        try {
            IntrospectionResponse introspection = restClient.get()
                    .uri("/introspect")
                    .header("Authorization", bearerToken)
                    .retrieve()
                    .body(IntrospectionResponse.class);
            boolean admin = hasAdminRole(introspection);
            log.debug("GET {} → valid token, admin={}", introspectUrl, admin);
            return admin;
        } catch (HttpClientErrorException e) {
            log.debug("GET {} → {} (unauthorized)", introspectUrl, e.getStatusCode().value());
            return false;
        } catch (RestClientException e) {
            log.error("GET {} → unreachable or unreadable: {}", introspectUrl, e.getMessage());
            return false;
        }
    }

    private static boolean hasAdminRole(IntrospectionResponse introspection) {
        if (introspection == null || introspection.roles() == null) {
            log.warn("GET /introspect answered 2xx with no roles — treating the token as non-admin");
            return false;
        }
        return introspection.roles().stream()
                .filter(Objects::nonNull)
                .anyMatch(ADMIN_ROLES::contains);
    }

    /** Only the roles are read; {@code sub} and {@code isAdmin} are ignored on purpose. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntrospectionResponse(List<String> roles) {
    }

    private static String mask(String bearer) {
        if (bearer == null || bearer.length() <= 14) return "***";
        return bearer.substring(0, 14) + "…";
    }
}
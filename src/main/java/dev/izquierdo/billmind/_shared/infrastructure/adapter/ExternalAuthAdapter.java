package dev.izquierdo.billmind._shared.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExternalAuthAdapter implements ExternalAuthPort {

    private static final Logger log = LoggerFactory.getLogger(ExternalAuthAdapter.class);

    private final RestClient restClient;
    private final String introspectUrl;

    public ExternalAuthAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.auth.external-url}") String externalUrl) {
        this.restClient = restClientBuilder.baseUrl(externalUrl).build();
        this.introspectUrl = externalUrl + "/introspect";
    }

    @Override
    public boolean isAuthorized(String bearerToken) {
        log.debug("GET {} — token: {}", introspectUrl, mask(bearerToken));
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri("/introspect")
                    .header("Authorization", bearerToken)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("GET {} → {}", introspectUrl, response.getStatusCode().value());
            return true;
        } catch (HttpClientErrorException e) {
            log.debug("GET {} → {} (unauthorized)", introspectUrl, e.getStatusCode().value());
            return false;
        } catch (RestClientException e) {
            log.error("GET {} → unreachable: {}", introspectUrl, e.getMessage());
            return false;
        }
    }

    private static String mask(String bearer) {
        if (bearer == null || bearer.length() <= 14) return "***";
        return bearer.substring(0, 14) + "…";
    }
}
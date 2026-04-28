package com.demo.billmind._shared.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Verifica que el servidor Ollama esté accesible.
 * Realiza una petición GET al endpoint raíz de Ollama y comprueba que responda.
 */
@Component
public class OllamaHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String ollamaBaseUrl;
    private final HttpClient httpClient;

    public OllamaHealthIndicator(@Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public Health health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 500) {
                return Health.up().withDetail("url", ollamaBaseUrl).build();
            }
            return Health.down().withDetail("url", ollamaBaseUrl).withDetail("status", response.statusCode()).build();

        } catch (Exception ex) {
            return Health.down().withDetail("url", ollamaBaseUrl).withException(ex).build();
        }
    }
}

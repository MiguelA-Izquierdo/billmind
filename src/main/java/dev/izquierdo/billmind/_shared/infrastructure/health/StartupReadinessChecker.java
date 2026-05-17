package dev.izquierdo.billmind._shared.infrastructure.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

@Component
public class StartupReadinessChecker {

    private static final Logger log = LoggerFactory.getLogger(StartupReadinessChecker.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final String llmProvider;
    private final String ollamaBaseUrl;
    private final String ollamaChatModel;
    private final String openAiApiKey;
    private final String anthropicApiKey;
    private final String geminiApiKey;
    private final String groqApiKey;

    public StartupReadinessChecker(
            DataSource dataSource,
            @Value("${llm.provider}") String llmProvider,
            @Value("${llm.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${llm.ollama.model:llama3.2}") String ollamaChatModel,
            @Value("${llm.openai.api-key:}") String openAiApiKey,
            @Value("${llm.anthropic.api-key:}") String anthropicApiKey,
            @Value("${llm.gemini.api-key:}") String geminiApiKey,
            @Value("${llm.groq.api-key:}") String groqApiKey) {
        this.dataSource = dataSource;
        this.llmProvider = llmProvider;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaChatModel = ollamaChatModel;
        this.openAiApiKey = openAiApiKey;
        this.anthropicApiKey = anthropicApiKey;
        this.geminiApiKey = geminiApiKey;
        this.groqApiKey = groqApiKey;
    }

    @PostConstruct
    public void check() {
        log.info("┌─ BillMind startup checks ───────────────────────");
        checkPgVector();
        checkLlmProvider();
        log.info("└─ All checks passed — ready to accept requests ──");
    }

    private void checkPgVector() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")) {
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new IllegalStateException(
                        "pgVector extension is not installed. " +
                        "Connect to PostgreSQL and run: CREATE EXTENSION vector;");
            }
            log.info("│  [OK] PostgreSQL + pgVector");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot verify pgVector extension: " + e.getMessage(), e);
        }
    }

    private void checkLlmProvider() {
        switch (llmProvider) {
            case "ollama"    -> checkOllama();
            case "openai"    -> requireApiKey("OpenAI",    openAiApiKey,    "OPENAI_API_KEY");
            case "anthropic" -> requireApiKey("Anthropic", anthropicApiKey, "ANTHROPIC_API_KEY");
            case "gemini"    -> requireApiKey("Gemini",    geminiApiKey,    "GEMINI_API_KEY");
            case "groq"      -> requireApiKey("Groq",      groqApiKey,      "GROQ_API_KEY");
            default -> throw new IllegalStateException(
                    "Unknown LLM_PROVIDER='" + llmProvider + "'. " +
                    "Valid values: ollama, openai, anthropic, gemini, groq");
        }
    }

    private void checkOllama() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        checkOllamaReachable(client);
        checkOllamaModelAvailable(client);
    }

    private void checkOllamaReachable(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/version"))
                    .GET().timeout(TIMEOUT).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 500) {
                throw new IllegalStateException("Ollama returned HTTP " + status + " at " + ollamaBaseUrl);
            }
            log.info("│  [OK] Ollama reachable at {}", ollamaBaseUrl);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Ollama is not reachable at " + ollamaBaseUrl + ". " +
                    "Start Ollama or run: docker-compose --profile local-ai up -d", e);
        }
    }

    private void checkOllamaModelAvailable(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET().timeout(TIMEOUT).build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

            String baseModelName = ollamaChatModel.split(":")[0];
            JsonNode models = MAPPER.readTree(body).path("models");
            for (JsonNode model : models) {
                if (model.path("name").asText().split(":")[0].equals(baseModelName)) {
                    log.info("│  [OK] Ollama model '{}' available", ollamaChatModel);
                    return;
                }
            }
            throw new IllegalStateException(
                    "Chat model '" + ollamaChatModel + "' is not pulled in Ollama. " +
                    "Run: ollama pull " + ollamaChatModel);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not retrieve Ollama model list (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private void requireApiKey(String provider, String apiKey, String envVar) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    provider + " API key is missing. Set " + envVar + " in your .env file.");
        }
        log.info("│  [OK] {} — API key configured", provider);
    }
}
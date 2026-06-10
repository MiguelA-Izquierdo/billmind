package dev.izquierdo.billmind._shared.infrastructure.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class StartupReadinessChecker {

    private static final Logger log = LoggerFactory.getLogger(StartupReadinessChecker.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final EmbeddingModel embeddingModel;
    private final String llmProvider;
    private final String ollamaBaseUrl;
    private final String ollamaChatModel;
    private final String openAiApiKey;
    private final String anthropicApiKey;
    private final String geminiApiKey;
    private final String groqApiKey;
    private final boolean kafkaEnabled;
    private final String kafkaBootstrapServers;
    private final String embeddingProvider;
    private final String ollamaEmbeddingModel;
    private final int vectorDimensions;
    private final String vectorTable;

    public StartupReadinessChecker(
            DataSource dataSource,
            EmbeddingModel embeddingModel,
            @Value("${llm.provider}") String llmProvider,
            @Value("${llm.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${llm.ollama.model:llama3.2}") String ollamaChatModel,
            @Value("${llm.openai.api-key:}") String openAiApiKey,
            @Value("${llm.anthropic.api-key:}") String anthropicApiKey,
            @Value("${llm.gemini.api-key:}") String geminiApiKey,
            @Value("${llm.groq.api-key:}") String groqApiKey,
            @Value("${kafka.enabled:false}") boolean kafkaEnabled,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrapServers,
            @Value("${embedding.provider:allminilm}") String embeddingProvider,
            @Value("${embedding.ollama.model:nomic-embed-text}") String ollamaEmbeddingModel,
            @Value("${pgvector.dimensions:384}") int vectorDimensions,
            @Value("${pgvector.table-name:vector_store}") String vectorTable) {
        this.dataSource            = dataSource;
        this.embeddingModel        = embeddingModel;
        this.llmProvider           = llmProvider;
        this.ollamaBaseUrl         = ollamaBaseUrl;
        this.ollamaChatModel       = ollamaChatModel;
        this.openAiApiKey          = openAiApiKey;
        this.anthropicApiKey       = anthropicApiKey;
        this.geminiApiKey          = geminiApiKey;
        this.groqApiKey            = groqApiKey;
        this.kafkaEnabled          = kafkaEnabled;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.embeddingProvider     = embeddingProvider;
        this.ollamaEmbeddingModel  = ollamaEmbeddingModel;
        this.vectorDimensions      = vectorDimensions;
        this.vectorTable           = vectorTable;
    }

    @PostConstruct
    public void check() {
        log.info("┌─ BillMind startup checks ───────────────────────");
        checkPgVector();
        checkLlmProvider();
        checkEmbeddingProvider();
        if (kafkaEnabled) {
            checkKafka();
        }
        log.info("└─ All checks passed — ready to accept requests ──");
    }

    private void checkPgVector() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'");
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new IllegalStateException(
                        "pgVector extension is not installed. " +
                        "Connect to PostgreSQL and run: CREATE EXTENSION vector;");
            }
            rs.close();
            stmt.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            log.info("│  [OK] PostgreSQL + pgVector + unaccent");
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

    private void checkEmbeddingProvider() {
        switch (embeddingProvider) {
            case "allminilm" -> {} // local ONNX — available if the dep is on the classpath
            case "openai"    -> requireApiKey("OpenAI (embeddings)", openAiApiKey, "OPENAI_API_KEY");
            case "ollama"    -> checkOllamaEmbedding();
            default -> throw new IllegalStateException(
                    "Unknown EMBEDDING_PROVIDER='" + embeddingProvider + "'. " +
                    "Valid values: allminilm, openai, ollama");
        }
        checkEmbeddingDimensions();
    }

    private void checkEmbeddingDimensions() {
        int modelDim = embeddingModel.embed("test").content().vector().length;
        if (modelDim != vectorDimensions) {
            throw new IllegalStateException(String.format(
                    "Embedding model produces %d-d vectors but PGVECTOR_DIMENSIONS=%d. " +
                    "Set PGVECTOR_DIMENSIONS=%d in your .env file.",
                    modelDim, vectorDimensions, modelDim));
        }
        checkVectorStoreColumnDimension(modelDim);
        log.info("│  [OK] Embedding — provider={}, dimensions={}", embeddingProvider, modelDim);
    }

    private void checkVectorStoreColumnDimension(int expected) {
        String sql = "SELECT atttypmod FROM pg_attribute " +
                     "WHERE attrelid = ?::regclass AND attname = 'embedding'";
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vectorTable);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return; // table does not exist yet — PgVectorEmbeddingStore will create it
                }
                int actual = rs.getInt(1);
                if (actual != expected) {
                    throw new IllegalStateException(String.format(
                            "Table '%s' has embedding column of %d dimensions but model produces %d. " +
                            "Run: DROP TABLE %s; and restart to let it be recreated with the correct schema.",
                            vectorTable, actual, expected, vectorTable));
                }
            }
        } catch (java.sql.SQLException e) {
            // regclass cast throws if the table doesn't exist — treat as not yet created
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                return;
            }
            throw new IllegalStateException("Could not verify vector store column dimensions: " + e.getMessage(), e);
        }
    }

    private void checkOllama() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        checkOllamaReachable(client);
        checkOllamaModelAvailable(client, ollamaChatModel);
    }

    private void checkOllamaEmbedding() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        checkOllamaReachable(client);
        checkOllamaModelAvailable(client, ollamaEmbeddingModel);
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

    private void checkOllamaModelAvailable(HttpClient client, String modelName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET().timeout(TIMEOUT).build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

            String baseModelName = modelName.split(":")[0];
            JsonNode models = MAPPER.readTree(body).path("models");
            for (JsonNode model : models) {
                if (model.path("name").asText().split(":")[0].equals(baseModelName)) {
                    log.info("│  [OK] Ollama model '{}' available", modelName);
                    return;
                }
            }
            throw new IllegalStateException(
                    "Model '" + modelName + "' is not pulled in Ollama. " +
                    "Run: ollama pull " + modelName);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Could not retrieve Ollama model list (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private void checkKafka() {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                       AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis(),
                       AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis()))) {

            admin.describeCluster().clusterId().get((int) TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            log.info("│  [OK] Kafka reachable at {}", kafkaBootstrapServers);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Kafka is not reachable at " + kafkaBootstrapServers + ". " +
                    "Start Kafka or set KAFKA_ENABLED=false to disable it.", ex);
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
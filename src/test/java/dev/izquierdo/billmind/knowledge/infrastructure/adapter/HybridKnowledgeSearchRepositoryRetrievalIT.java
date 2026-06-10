package dev.izquierdo.billmind.knowledge.infrastructure.adapter;

import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.knowledge.domain.model.KnowledgeSearchResult;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeSearchRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for retrieval quality.
// Seeds the knowledge base with 6 regulatory documents and evaluates recall@k and MRR
// against a curated golden query set. Thresholds gate quality regressions in CI.
//
// Real stack: pgvector (TestContainers), AllMiniLM-L6-v2 (local ONNX), HybridKnowledgeSearchRepository.
// Mocked: StartupReadinessChecker (health probes), InvoiceFieldExtractor (avoids LLM calls).
// Kafka: disabled via kafka.enabled=false (default in test application.properties).
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "knowledge.seed.enabled=true"
})
class HybridKnowledgeSearchRepositoryRetrievalIT {

    private static final Logger log = LoggerFactory.getLogger(HybridKnowledgeSearchRepositoryRetrievalIT.class);

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.host",     postgres::getHost);
        registry.add("spring.datasource.port",     () -> postgres.getMappedPort(5432).toString());
        registry.add("spring.datasource.database", postgres::getDatabaseName);
    }

    @MockitoBean StartupReadinessChecker startupReadinessChecker;
    @MockitoBean InvoiceFieldExtractor   invoiceFieldExtractor;

    @Autowired KnowledgeSearchRepository searchRepository;

    // ---------------------------------------------------------------------------
    // Golden query set — 8 representative queries mapped to expected document titles
    // ---------------------------------------------------------------------------

    record QueryCase(String query, Set<String> acceptedKeywords) {
        boolean matchesTitle(String title) {
            String lower = title.toLowerCase();
            return acceptedKeywords.stream().anyMatch(lower::contains);
        }
    }

    record QueryResult(QueryCase queryCase, List<KnowledgeSearchResult> results) {
        boolean isHit() {
            return results.stream().anyMatch(r -> queryCase.matchesTitle(r.title()));
        }
        double reciprocalRank() {
            for (int i = 0; i < results.size(); i++) {
                if (queryCase.matchesTitle(results.get(i).title())) return 1.0 / (i + 1);
            }
            return 0.0;
        }
    }

    private static final List<QueryCase> GOLDEN_SET = List.of(
            new QueryCase("¿Qué es el CUPS en mi factura?",
                    Set.of("glosario", "cómo leer")),
            new QueryCase("qué significa el término de potencia en la factura de luz",
                    Set.of("glosario", "cómo leer")),
            new QueryCase("horas punta llano valle discriminación horaria electricidad",
                    Set.of("2.0td")),
            new QueryCase("qué es la potencia P1 P2 contratada tarifa 2.0TD",
                    Set.of("2.0td", "cómo leer")),
            new QueryCase("¿me conviene el PVPC o tarifa fija del mercado libre?",
                    Set.of("pvpc", "frecuentes")),
            new QueryCase("peajes acceso red CNMC metodología cargos sistema eléctrico",
                    Set.of("peajes", "metodología")),
            new QueryCase("cómo leer entender factura electricidad conceptos desglose importe",
                    Set.of("cómo leer", "frecuentes")),
            new QueryCase("por qué me ha subido tanto la factura de luz este mes",
                    Set.of("frecuentes", "cómo leer"))
    );

    private static final double MIN_RECALL_K3 = 0.625; // ≥ 5 / 8 queries hit at k=3
    private static final double MIN_RECALL_K5 = 0.750; // ≥ 6 / 8 queries hit at k=5
    private static final double MIN_MRR_K5    = 0.500;

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    void shouldMeetRecallAtThreeThreshold() {
        List<QueryResult> run = runGoldenSet(3);
        double recall = recall(run);
        log.info("Retrieval quality:\n{}", formatReport(run, 3, recall, null));
        assertThat(recall)
                .as("recall@3 must be ≥ %.3f but was %.3f", MIN_RECALL_K3, recall)
                .isGreaterThanOrEqualTo(MIN_RECALL_K3);
    }

    @Test
    void shouldMeetRecallAtFiveThreshold() {
        List<QueryResult> run = runGoldenSet(5);
        double recall = recall(run);
        log.info("Retrieval quality:\n{}", formatReport(run, 5, recall, null));
        assertThat(recall)
                .as("recall@5 must be ≥ %.3f but was %.3f", MIN_RECALL_K5, recall)
                .isGreaterThanOrEqualTo(MIN_RECALL_K5);
    }

    @Test
    void shouldMeetMeanReciprocalRankThreshold() {
        List<QueryResult> run = runGoldenSet(5);
        double mrr = mrr(run);
        log.info("Retrieval quality:\n{}", formatReport(run, 5, null, mrr));
        assertThat(mrr)
                .as("MRR@5 must be ≥ %.3f but was %.3f", MIN_MRR_K5, mrr)
                .isGreaterThanOrEqualTo(MIN_MRR_K5);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private List<QueryResult> runGoldenSet(int k) {
        return GOLDEN_SET.stream()
                .map(qc -> new QueryResult(qc, searchRepository.search(qc.query(), k)))
                .toList();
    }

    private double recall(List<QueryResult> run) {
        long hits = run.stream().filter(QueryResult::isHit).count();
        return (double) hits / run.size();
    }

    private double mrr(List<QueryResult> run) {
        return run.stream()
                .mapToDouble(QueryResult::reciprocalRank)
                .average()
                .orElse(0.0);
    }

    private String formatReport(List<QueryResult> run, int k, Double recall, Double mrr) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  k=%d  recall@k=%-6s  MRR@k=%-6s%n",
                k,
                recall != null ? String.format("%.3f", recall) : "-",
                mrr    != null ? String.format("%.3f", mrr)    : "-"));
        for (QueryResult r : run) {
            String titles = r.results().stream()
                    .map(KnowledgeSearchResult::title)
                    .collect(Collectors.joining(" | "));
            sb.append(String.format("  [%s] %s%n       → %s%n",
                    r.isHit() ? "HIT " : "MISS", r.queryCase().query(), titles));
        }
        return sb.toString();
    }
}
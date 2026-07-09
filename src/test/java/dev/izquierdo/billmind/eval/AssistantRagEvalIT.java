package dev.izquierdo.billmind.eval;

import dev.izquierdo.billmind._shared.infrastructure.health.StartupReadinessChecker;
import dev.izquierdo.billmind.assistant.application.service.ChatContextAssembler;
import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RAGAS-style end-to-end quality gate for the assistant's regulatory RAG pipeline.
 *
 * <p><b>Hybrid design.</b> Two layers over the ~50-case golden set
 * ({@code src/test/resources/eval/rag_eval_dataset.json}):
 * <ul>
 *   <li><b>Deterministic (always runs).</b> No cloud LLM needed — retrieval uses the local
 *       AllMiniLM-L6-v2 ONNX model against pgvector. Scores context precision (docType Average
 *       Precision), context recall (docType hit@k) and reference coverage (max cosine between the
 *       ground-truth answer and a retrieved chunk). These gate every CI run.</li>
 *   <li><b>LLM-judge (opt-in).</b> Enabled with {@code EVAL_LLM_ENABLED=true} and a reachable
 *       chat model. Generates real answers via the assistant pipeline and scores faithfulness
 *       (LLM-as-judge claim verification), answer relevancy (cosine question↔answer) and fact
 *       coverage. Skipped (not failed) otherwise.</li>
 * </ul>
 *
 * Thresholds are calibrated for AllMiniLM-L6-v2, which underperforms production-grade embedding
 * models on Spanish regulatory text; tighten them once CI runs with a stronger embedder.
 * Mirrors the infra of {@code RagGoldenSetIT} (pgvector TestContainers, seeded corpus).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "knowledge.seed.enabled=true",
        "knowledge.search.min-vector-score=0.3"
})
class AssistantRagEvalIT {

    private static final Logger log = LoggerFactory.getLogger(AssistantRagEvalIT.class);

    /** Opt-in switch for the LLM-judge layer. Deterministic metrics run regardless. */
    private static final boolean LLM_ENABLED = "true".equalsIgnoreCase(System.getenv("EVAL_LLM_ENABLED"));

    // --- Deterministic gate thresholds (AllMiniLM-L6-v2; recalibrate for production embedder) ---
    // Calibrated with headroom below the observed baseline over the 50-case set:
    // ctxPrecision≈0.82, ctxRecall≈1.00, refCoverage≈0.73. Tighten as retrieval improves.
    private static final double MIN_CONTEXT_PRECISION  = 0.70;
    private static final double MIN_CONTEXT_RECALL     = 0.90;
    private static final double MIN_REFERENCE_COVERAGE = 0.62;

    // --- LLM-judge gate thresholds (only asserted when EVAL_LLM_ENABLED=true) ---
    private static final double MIN_FAITHFULNESS       = 0.65;
    private static final double MIN_ANSWER_RELEVANCY   = 0.45;
    private static final double MIN_FACT_COVERAGE      = 0.55;

    // -------------------------------------------------------------------------
    // Infrastructure (mirrors RagGoldenSetIT)
    // -------------------------------------------------------------------------

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

    @Autowired ChatContextAssembler assembler;
    @Autowired EmbeddingModel        embeddingModel;
    @Autowired AssistantLlmPort      llmPort;
    @Autowired(required = false) @Qualifier("smartChatModel") ChatModel smartChatModel;

    // -------------------------------------------------------------------------
    // Harness — run once, assert per-metric in each @Test
    // -------------------------------------------------------------------------

    private static final List<RagEvalCase> GOLDEN = RagEvalCase.loadAll();

    /** All metrics for one golden case. LLM-layer metrics are {@code NaN} when the layer is off. */
    record CaseEval(
            RagEvalCase c,
            List<String> retrievedDocTypes,
            double contextPrecision,
            double contextRecall,
            double referenceCoverage,
            double faithfulness,
            double answerRelevancy,
            double factCoverage
    ) {}

    private List<CaseEval> results;

    @BeforeAll
    void runHarness() {
        EvalEmbeddings emb = new EvalEmbeddings(embeddingModel);
        EvalLlmJudge judge = (LLM_ENABLED && smartChatModel != null) ? new LlmEvalJudge(smartChatModel) : null;
        log.info("[EVAL] RAGAS harness — {} cases, LLM-judge layer {}",
                GOLDEN.size(), judge != null ? "ENABLED" : "disabled");
        results = GOLDEN.stream().map(c -> evaluate(c, emb, judge)).toList();
        log.info("RAGAS eval report:\n{}", report());
    }

    private CaseEval evaluate(RagEvalCase c, EvalEmbeddings emb, EvalLlmJudge judge) {
        ChatContext ctx = assembler.assemble(null, null, c.question());
        List<RegulatorySnippet> snippets = ctx.regulatoryContext();
        List<String> docTypes = snippets.stream().map(RegulatorySnippet::docType).toList();
        List<String> contents = snippets.stream().map(RegulatorySnippet::content).toList();

        double precision = RagasMetrics.contextPrecision(c.expectedDocTypes(), docTypes);
        double recall    = RagasMetrics.contextRecall(c.expectedDocTypes(), docTypes);
        double coverage  = contents.stream()
                .mapToDouble(content -> emb.similarity(c.referenceAnswer(), content))
                .max().orElse(0.0);

        double faithfulness = Double.NaN, relevancy = Double.NaN, factCoverage = Double.NaN;
        if (judge != null) {
            String answer = llmPort.answer(ctx, c.question(), List.of()).answer();
            faithfulness = judge.faithfulness(answer, contents);
            relevancy    = emb.similarity(c.question(), answer);
            factCoverage = RagasMetrics.factCoverage(c.mustIncludeFacts(), answer);
        }
        return new CaseEval(c, docTypes, precision, recall, coverage, faithfulness, relevancy, factCoverage);
    }

    // -------------------------------------------------------------------------
    // Deterministic gates — always run
    // -------------------------------------------------------------------------

    @Test
    void shouldMeetContextPrecisionGate() {
        double avg = mean(CaseEval::contextPrecision);
        assertThat(avg)
                .as("mean context precision ≥ %.2f, got %.3f", MIN_CONTEXT_PRECISION, avg)
                .isGreaterThanOrEqualTo(MIN_CONTEXT_PRECISION);
    }

    @Test
    void shouldMeetContextRecallGate() {
        double avg = mean(CaseEval::contextRecall);
        assertThat(avg)
                .as("mean context recall ≥ %.2f, got %.3f", MIN_CONTEXT_RECALL, avg)
                .isGreaterThanOrEqualTo(MIN_CONTEXT_RECALL);
    }

    @Test
    void shouldMeetReferenceCoverageGate() {
        double avg = mean(CaseEval::referenceCoverage);
        assertThat(avg)
                .as("mean reference coverage (cosine) ≥ %.2f, got %.3f", MIN_REFERENCE_COVERAGE, avg)
                .isGreaterThanOrEqualTo(MIN_REFERENCE_COVERAGE);
    }

    // -------------------------------------------------------------------------
    // LLM-judge gate — opt-in
    // -------------------------------------------------------------------------

    @Test
    void shouldMeetFaithfulnessGateWhenLlmEnabled() {
        assumeTrue(LLM_ENABLED, "EVAL_LLM_ENABLED not set — skipping LLM-judge faithfulness gate");
        double avg = meanIgnoringNaN(CaseEval::faithfulness);
        assertThat(avg)
                .as("mean faithfulness ≥ %.2f, got %.3f", MIN_FAITHFULNESS, avg)
                .isGreaterThanOrEqualTo(MIN_FAITHFULNESS);
    }

    @Test
    void shouldMeetAnswerRelevancyGateWhenLlmEnabled() {
        assumeTrue(LLM_ENABLED, "EVAL_LLM_ENABLED not set — skipping answer relevancy gate");
        double avg = meanIgnoringNaN(CaseEval::answerRelevancy);
        assertThat(avg)
                .as("mean answer relevancy ≥ %.2f, got %.3f", MIN_ANSWER_RELEVANCY, avg)
                .isGreaterThanOrEqualTo(MIN_ANSWER_RELEVANCY);
    }

    @Test
    void shouldMeetFactCoverageGateWhenLlmEnabled() {
        assumeTrue(LLM_ENABLED, "EVAL_LLM_ENABLED not set — skipping fact coverage gate");
        double avg = meanIgnoringNaN(CaseEval::factCoverage);
        assertThat(avg)
                .as("mean fact coverage ≥ %.2f, got %.3f", MIN_FACT_COVERAGE, avg)
                .isGreaterThanOrEqualTo(MIN_FACT_COVERAGE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double mean(ToDoubleFunction<CaseEval> metric) {
        return results.stream().mapToDouble(metric).average().orElse(0.0);
    }

    private double meanIgnoringNaN(ToDoubleFunction<CaseEval> metric) {
        return results.stream().mapToDouble(metric).filter(d -> !Double.isNaN(d)).average().orElse(0.0);
    }

    private String report() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  cases=%d  ctxPrecision=%.3f  ctxRecall=%.3f  refCoverage=%.3f%n",
                results.size(), mean(CaseEval::contextPrecision), mean(CaseEval::contextRecall),
                mean(CaseEval::referenceCoverage)));
        if (LLM_ENABLED) {
            sb.append(String.format("  faithfulness=%.3f  answerRelevancy=%.3f  factCoverage=%.3f%n",
                    meanIgnoringNaN(CaseEval::faithfulness), meanIgnoringNaN(CaseEval::answerRelevancy),
                    meanIgnoringNaN(CaseEval::factCoverage)));
        }
        for (CaseEval r : results) {
            sb.append(String.format("  [%s] %-5s P=%.2f R=%.0f cov=%.2f | expected=%s | got=%s%n",
                    r.c().difficulty(), r.c().id(), r.contextPrecision(), r.contextRecall(),
                    r.referenceCoverage(), r.c().expectedDocTypes(), r.retrievedDocTypes()));
        }
        return sb.toString();
    }
}
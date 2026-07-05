# BillMind — Evaluation Harness (RAGAS)

Reference for the RAGAS-style quality gate over the assistant's regulatory RAG pipeline
(Milestone 6). For LLM logging/metrics observability see `@docs/OBSERVABILITY.md`; for the
roadmap see `@docs/PLAN.md`.

---

## What it measures

The harness scores the `assistant/` retrieval + generation pipeline against a curated golden
set of ~50 Spanish Q&A about the electricity regulatory corpus (CNMC, REE, BOE, glossary,
general guide). It is **hybrid**: a deterministic layer that always gates CI plus an opt-in
LLM-as-judge layer.

### Deterministic layer (always runs)

No cloud LLM required — retrieval uses the same local **AllMiniLM-L6-v2** ONNX embedding model
as production, against pgvector (TestContainers). Metrics are computed against the golden
set's **reference answers** and **expected docTypes**, so no answer generation is needed.

| Metric | Meaning | RAGAS analogue |
|---|---|---|
| **Context precision** | Average Precision @k of retrieved chunks whose `docType` is expected — rewards ranking relevant chunks first | context precision |
| **Context recall** | Fraction of cases where an expected `docType` appears in the top-k | context recall |
| **Reference coverage** | Max cosine similarity between the ground-truth answer and any retrieved chunk — did retrieval fetch material that supports the reference answer? | context recall (semantic) |

### LLM-judge layer (opt-in)

Enabled with `EVAL_LLM_ENABLED=true` and a reachable chat model (`smartChatModel`). Generates
real answers through the assistant pipeline and scores:

| Metric | Meaning | How |
|---|---|---|
| **Faithfulness** | Fraction of the answer's atomic claims entailed by the retrieved context (anti-hallucination) | LLM-as-judge — claim decomposition + verification (`LlmEvalJudge`) |
| **Answer relevancy** | Cosine similarity between the question and the generated answer | embedding |
| **Fact coverage** | Fraction of `mustIncludeFacts` present in the answer (accent/case-insensitive) | deterministic string match |

When the layer is off, these gates are **skipped** (JUnit `assumeTrue`), never failed.

---

## Layout

```
src/test/resources/eval/rag_eval_dataset.json   # ~50 golden Q&A (question, referenceAnswer,
                                                # expectedDocTypes, mustIncludeFacts, difficulty)
src/test/java/dev/izquierdo/billmind/eval/
├── RagEvalCase.java        # record + classpath loader
├── EvalEmbeddings.java     # EmbeddingModel wrapper + cosine
├── RagasMetrics.java       # pure deterministic scorers (precision, recall, fact coverage)
├── EvalLlmJudge.java       # judge port
├── LlmEvalJudge.java       # ChatModel-backed faithfulness judge (sandwich prompt)
├── RagasMetricsTest.java   # pure unit tests for the scoring math + judge parser
└── AssistantRagEvalIT.java # SpringBootTest gate (pgvector TestContainers)
```

The harness lives in **test scope** — it is a CI quality gate, not runtime code, so no eval
or judge code ships in the production jar. This mirrors `RagGoldenSetIT` (the retrieval-only
recall gate that predates this harness; this one adds generation-side RAGAS metrics).

---

## Running

```bash
# Deterministic gate only (no LLM) — this is what CI runs by default
./mvnw -Dit.test=AssistantRagEvalIT -DfailIfNoTests=false verify

# Full RAGAS incl. faithfulness/relevancy/fact-coverage (needs a reachable chat model)
EVAL_LLM_ENABLED=true ./mvnw -Dit.test=AssistantRagEvalIT -DfailIfNoTests=false verify

# Pure metric unit tests (no Docker, no Spring)
./mvnw -Dtest=RagasMetricsTest test
```

The harness logs a per-case report (`RAGAS eval report: ...`) with the mean of each metric
and, per case, precision / recall / coverage and expected vs. retrieved docTypes.

---

## Thresholds & calibration

Thresholds are constants in `AssistantRagEvalIT` and are **calibrated for AllMiniLM-L6-v2**,
which underperforms production-grade embedders on Spanish regulatory text. Current observed
baseline over the 50-case set and the gate thresholds (with headroom to avoid flakiness):

| Metric | Observed | Gate |
|---|---|---|
| Context precision | ≈ 0.82 | ≥ 0.70 |
| Context recall | ≈ 1.00 | ≥ 0.90 |
| Reference coverage | ≈ 0.73 | ≥ 0.62 |

Deterministic-layer scores are stable (ONNX is deterministic). When CI moves to a stronger
embedding model (`mxbai-embed-large`, OpenAI `text-embedding-3-small`), tighten these. The
`knowledge.search.min-vector-score` is lowered to `0.3` in the test (same as `RagGoldenSetIT`)
because AllMiniLM scores lower on Spanish than the production default of `0.72`.

The LLM-judge thresholds (faithfulness ≥ 0.65, answer relevancy ≥ 0.45, fact coverage ≥ 0.55)
are starting points — recalibrate once a fixed eval model is pinned in CI.

---

## Not yet done (Milestone 6 remainder)

- `eval_runs` persistence table (historical metric tracking) — deferred; the gate is
  stateless today.
- Langfuse self-hosted tracing — separate Milestone 6 deliverable, not part of this harness.
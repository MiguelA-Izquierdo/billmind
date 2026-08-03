# Engineering details

Detail moved out of the [README](../README.md): implementation notes per feature, the RAG thresholds enforced in CI, the domain-event bus, the security model and observability.

**Contents:** [Implementation notes](#implementation-notes) · [Quality gates](#quality-gates) · [Domain events](#domain-events) · [Security model](#security-model) · [Observability](#observability)

---

## Implementation notes

Each item links to its own deep dive:

- **Hybrid AI classifier that minimizes LLM calls** — keywords handle the obvious cases; the LLM is invoked only for ambiguous documents. → [Extraction pipeline](PLAN.md#milestone-1--structured-extraction--pii-redaction--complete)
- **Typed extraction, not string parsing** — a per-supply-type prompt returns JSON that is sanitized, deserialized into the sealed `InvoiceFields` record hierarchy and domain-validated; a malformed response gets one JSON-repair round trip before the extraction fails. → [Extraction pipeline](PLAN.md#milestone-1--structured-extraction--pii-redaction--complete)
- **Deterministic savings math** — overpayment, TOU weighting and cheapest-tariff selection are pure Java; the LLM only *explains* the result. → [Savings engine](PLAN.md#milestone-3--comparison-module--savings-engine--complete)
- **Event-driven market ingestion** — a Kafka (KRaft) consumer persists live rates idempotently (a replayed event hits the unique constraint and is dropped, not rewritten), with DLT + domain-error topics. → [`MARKET.md`](MARKET.md)
- **Hybrid retrieval (pgVector + BM25 + RRF)** — cosine similarity fused with Postgres `tsvector` BM25 via Reciprocal Rank Fusion, gated in CI by a per-difficulty recall@5 bar ([numbers below](#quality-gates)). → [Knowledge base](PLAN.md#milestone-2--knowledge-base-ingestion--hybrid-search--market-price-consumer--complete)
- **Agentic tool calling with a manual tool loop** — the assistant decides *what* to retrieve per question via a hardened low-level loop that keeps instrumentation, ports and citations. → [`ASSISTANT.md`](ASSISTANT.md)
- **RAGAS-style eval harness as a CI gate** — a deterministic embedding layer always gates `mvn verify` ([numbers below](#quality-gates)), plus an opt-in LLM-as-judge layer. → [`EVAL.md`](EVAL.md)
- **Swappable LLM provider — one env var** — each provider is a `@ConditionalOnProperty` bean (`LLM_PROVIDER=ollama|anthropic|openai|gemini|groq`); switching costs zero refactoring.
- **Layered security** — authenticate-in-the-filter / authorize-in-the-engine split, three-layer admin guard, per-endpoint token-bucket rate limiter, unified prompt-injection defenses. → [Security model](#security-model) · [`RATELIMIT.md`](RATELIMIT.md)
- **Full LLM observability** — every call timed and fanned out to composable sinks: Actuator `llm.*` meters plus opt-in OpenTelemetry spans to Langfuse. → [`OBSERVABILITY.md`](OBSERVABILITY.md)

---

## Quality gates

These thresholds are constants in the test sources, and a value below any of them **fails `./mvnw verify` and the Jenkins build** before an image is ever pushed.

| Metric | Gate (build fails below) | Current | Evaluated over |
|---|:-:|:-:|---|
| **Context precision** — AP@k of chunks whose `docType` is expected | ≥ 0.70 | **0.82** | 50-case Spanish golden set |
| **Context recall** — expected `docType` present in top-k | ≥ 0.90 | **1.00** | 50-case Spanish golden set |
| **Reference coverage** — max cosine, ground-truth answer ↔ retrieved chunk | ≥ 0.62 | **0.73** | 50-case Spanish golden set |
| **Retrieval recall@5** — hybrid pgVector + BM25 + RRF | ≥ 0.70 | **0.97** (29/30) | 30-question retrieval set, also gated per difficulty tier |

Two caveats about the table:

- **The gap between gate and current value is headroom, not slack.** Both suites run on **AllMiniLM-L6-v2** (384-dim, local ONNX) so CI needs no cloud LLM and stays deterministic — and that model underperforms production-grade embedders on Spanish regulatory text. Thresholds sit below the observed baseline so a legitimate 2-point drift doesn't turn CI red; the tighter targets to restore once a stronger embedder is pinned are written down next to the constants ([`RagGoldenSetIT`](../src/test/java/dev/izquierdo/billmind/knowledge/infrastructure/adapter/RagGoldenSetIT.java), [`AssistantRagEvalIT`](../src/test/java/dev/izquierdo/billmind/eval/AssistantRagEvalIT.java)).
- **MRR@5 is measured and logged per run, but not gated** — recall@5 is the bar that fails the build. The LLM-judge layer (faithfulness ≥ 0.65, answer relevancy ≥ 0.45, fact coverage ≥ 0.55) is opt-in via `EVAL_LLM_ENABLED` and **skipped, never failed**, when off: with no eval model pinned in CI, those thresholds are starting points and a published "current value" for them would be noise. Full methodology → [`EVAL.md`](EVAL.md).

---

## Domain events

Bounded contexts never call each other directly. When `invoice/` ingests a PDF or `assistant/` answers a question, it publishes a `DomainEvent` through a **synchronous in-process bus** (`DomainEventPublisher`), dispatched by exact event class. The `metrics/` context reacts to the upload funnel (`InvoiceIngested` / `InvoiceRejected`, by drop-off reason) and chat engagement (`AssistantQuestionAnswered`, where `citationCount == 0` doubles as a KB coverage-gap signal).

`InvoiceIngested` is published **after** the invoice is persisted, outside the narrow transaction that wrote it. The other two paths never reach the database — a rejected upload is dropped before persistence, and conversations live in memory — so there is no commit to wait for. Payloads carry **only ids, enums and counters — never invoice or message text** — so `metrics/` is PII-free by construction. The design principle and the deferred transactional-outbox trigger are in [`PLAN.md` → Cross-cutting Considerations](PLAN.md#4-cross-cutting-considerations).

---

## Security model

**Anonymous endpoints (Phase 1):** every request carries a client-generated UUID in `X-Session-Id`. The backend uses it to correlate resources (invoices, conversations) but does **not** authenticate the caller.

**Admin endpoints:** BillMind never validates tokens itself — authentication is fully delegated to an external identity microservice: [`user-service`](https://github.com/MiguelA-Izquierdo/user-service), a companion Spring Boot + DDD service written by the same author, whose introspection endpoint answers with the token's subject and roles (`ROLE_USER` / `ROLE_ADMIN` / `ROLE_SUPER_ADMIN`). Authenticating and authorizing are deliberately separate steps, layered three deep so no single bug opens an admin route. **(1)** `JwtAuthFilter` only establishes an identity: it calls `GET <AUTH_EXTERNAL_URL>/introspect` and puts an `ExternalTokenAuthentication` into the `SecurityContext` — it rejects nothing. **(2)** The access **decision** belongs to Spring Security's authorization engine, where `RouteAccessAuthorizationManager` classifies the request through `RouteAccessPolicy`. **(3)** `@PreAuthorize("hasRole('ADMIN')")` on each admin handler enforces it again, independently. A new `/api/v1/admin/**` route is guarded the moment it is mapped — no filter change. `anyRequest().permitAll()` is banned. On top of that, a **per-endpoint token-bucket rate limiter** (bucket4j + Caffeine, pre/post-auth checkpoints) guards every `/api/v1/**` route.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) → *Admin route protection* and [`RATELIMIT.md`](RATELIMIT.md). **Milestone 9** extends the same delegation model to user-facing endpoints rather than adding local JWT validation.

---

## Observability

Micrometer/Actuator metrics and structured logs, served on a separate internal-only management port. Logs stay in the infrastructure layer — no invoice content, PII or credentials, scrubbed at render time by a `%pii` logback converter. Metrics cover PII/classifier/upload timers plus the `llm.*` and `ratelimit.*` families; every LLM call is timed by `TimedChatLanguageModel` and fanned out to composable `LlmTelemetry` sinks (Actuator meters on by default, OTLP spans to Langfuse opt-in). Full reference → [`OBSERVABILITY.md`](OBSERVABILITY.md).
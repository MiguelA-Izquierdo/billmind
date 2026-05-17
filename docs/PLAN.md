# BillMind — Roadmap

Long-term plan for evolving BillMind from its current state (PDF ingestion + classification) into a complete RAG + LLM system. Living reference; updated when scope changes.

---

## 1. Product Vision

### Phase 1 — Anonymous (scope of this plan)

A visitor opens the app, uploads a utility invoice PDF, and the system:

- Extracts structured fields (price/kWh, contracted power, billing period, totals).
  - Compares the invoice against current market rate data (received in real-time via Kafka events from an external price service) to estimate potential savings.
  - Lets the user **chat** about their invoice. The assistant has access to the full invoice text and retrieves relevant context from a regulatory knowledge base (CNMC, REE, BOE) via RAG.

No login. The frontend generates a UUID per visitor and sends it as `X-Session-Id`. The backend correlates resources (uploaded invoices, conversations) to that UUID without authenticating it.

### Phase 2 — Authenticated (post-Milestone 7)

Visitors can register to get persistent history, multiple invoices over time, and personalized recommendations. Existing sessions can be attached to a newly created user account.

---

## 2. Stack — kept and added

**Kept:** Spring Boot 3.5.0, Java 21, LangChain4j 1.0.0, Ollama (local), PostgreSQL 16 + pgVector (IVFFlat, 384 dim), TestContainers. Hexagonal + DDD. CQRS with command bus. Domain events. `GlobalExceptionHandler`.

**Added per milestone:**

| Addition | Milestone | Why |
|---|---|---|
| Enriched `Invoice` aggregate + table | 0 | Today the classifier metadata is discarded |
| `sessions` table + `SessionContext` + `X-Session-Id` filter | 0 | Anonymous correlator, FK targets |
| LangChain4j `AiServices` + JSON Schema structured outputs | 1 | Move from string parsing to typed extraction |
| `PiiRedactor` applied to full invoice text | 1 | GDPR + dataset safety |
| Regulatory knowledge base ingestion pipeline | 2 | Populate the RAG corpus before the assistant can use it |
| Hybrid retrieval over knowledge base (pgVector + `tsvector` BM25 + RRF) | 2 | Pure vector search misses keyword-heavy regulatory queries |
| Cross-encoder re-ranker | 2 | Retrieval quality |
| Apache Kafka (KRaft) + `spring-kafka` | 2 | Event-driven market price ingestion — no polling |
| `market/` bounded context — `MarketRate` aggregate + `market_rates` table | 2 | Persist incoming price events for the comparison agent |
| `MarketPriceConsumer` — Kafka listener on `market.price-updated` | 2 | Prices arrive in real-time; the external producer is a separate service |
| `assistant/` bounded context | 3 | Conversational RAG — the demo "wow" |
| SSE streaming | 3 | Real LLM UX |
| Persistent conversational memory | 3 | Stateless design — no volatile in-process storage |
| LangChain4j `AiServices` with `@Tool` (agent) | 5 | Agents over monolithic prompts |
| Eval harness + golden set + RAGAS-style metrics | 6 | Quality regression in CI |
| Langfuse self-hosted | 6 | LLM observability |
| Spring Security + JWT + rate limiting | 7 | Phase 2 |
| Semantic response cache | 7 | Cost / latency |
| Flyway migrations (consolidated initial migration) | 7 | Production-grade deployability before first release |

---

## 3. Data Model (final shape)

```
sessions (id UUID PK, first_seen_at, last_seen_at)
   │
   ├── invoices (id, session_id FK, supply_type, provider,
   │            billing_period_start/end, total_amount,
   │            consumption_kwh, price_per_kwh, contracted_power,
   │            uploaded_at, raw_text_redacted, file_name)
   │
   └── conversations (id, session_id FK, created_at)
          └── messages (id, conversation_id FK, role, content,
                       citations[], created_at, tokens_in, tokens_out)

market_rates (id, supply_type, provider, tariff_name,
              price_per_kwh, valid_from, valid_to, region, source)

comparisons (id, invoice_id FK, session_id FK, result_json,
             tools_called[], created_at, model_used)

knowledge_base (id, source, doc_type, title, url, valid_from, valid_to)
   └── vector_store (chunks + embedding + metadata
                    {doc_id, doc_type, source, section})

eval_runs (id, golden_set_version, faithfulness, context_precision,
           answer_relevancy, created_at)  -- Milestone 6
```

`invoices` contains **no PII** — redaction happens at extraction time (Milestone 1).

**Note on shared energy columns:** `consumption_kwh` and `price_per_kwh` in the `invoices` table are reused for gas invoices (`GasFields`) in addition to electricity (`ElectricityFields`). Gas invoices also populate `consumption_m3` (the native unit), but the energy-equivalent kWh and the price per kWh are stored in the same columns as electricity. This is an intentional short-term trade-off: a single flat table is simpler to query and avoids premature normalization during active development. The downside is that analytics queries (e.g. "average price per kWh for gas vs electricity") must filter on `supply_type` to avoid mixing units — this is non-obvious from the schema alone. Milestone 7 (schema consolidation with Flyway) is the right moment to revisit this: options are (a) per-supply-type subtables or a `invoice_fields` JSONB column, (b) a dedicated `electricity_fields` / `gas_fields` side-table keyed on `invoice_id`, or (c) keeping the flat schema with a clear column naming convention (`energy_kwh`, `energy_price_per_kwh`) and a view per supply type for analytics.

**Note on vector store scope:** invoice chunks are NOT vectorized. The vector store holds the regulatory knowledge base (CNMC tariff methodology, REE documentation, BOE regulations). The chat assistant uses dual context: the user's full invoice text passed directly (it fits in any modern context window) + semantic retrieval from the knowledge base for regulatory questions. This avoids forcing RAG onto structured numerical data where SQL aggregations are more reliable.

---

## 4. Cross-cutting Considerations

1. **Dataset anonymity.** The corpus is a shared aggregate. `session_id` is traceability, not authorization.
   2. **Mandatory PII redaction** before persisting any invoice or chunk. Tested explicitly.
   3. **Minimal authorization for conversations.** A `GET /conversations/{id}` only responds if the request `X-Session-Id` matches the row. This is the only "auth" in Phase 1.
   4. **Determinism where possible.** The LLM reasons; numerical math (annual savings) lives in deterministic Java tools. Never ask the LLM to multiply.
   5. **Mandatory citations.** Every assistant reply cites the knowledge base documents it used. No citation, no render.
   6. **Cost / latency awareness.** Token budgets. Small models for classification; larger for reasoning.
   7. **Prompt injection defense.** User input is never concatenated into the system prompt. Sandwich pattern: instructions → delimited data → instructions.
   8. **Evaluation as a first-class citizen.** Golden set grows with each milestone. CI fails on quality regression.
   9. **Observability from Milestone 6.** Structured traces, tokens, latencies, estimated cost per session.
   10. **Hexagonal rules untouchable.** No Spring/LangChain4j imports in `domain/`. Ever.

---

## 5. Milestones

> Each milestone is independently shippable and demoable.

### Milestone 0 — Foundations ✓ COMPLETE

**Objective:** persistence and session infrastructure to build everything on.

**Deliverables:**

- `sessions` table + upsert on every request.
  - `SessionContext` request-scoped bean + `X-Session-Id` validation filter.
  - Enriched `Invoice` aggregate + table.
  - `InvoiceRepository` (port + JPA adapter).
  - Persist classification (currently discarded) in `UploadInvoiceUseCase`.
  - `GET /api/v1/invoices` and `GET /api/v1/invoices/{id}`.

**Dependencies:** none.

**Note on schema management:** during Milestones 0–6 the schema is owned by JPA entities (`ddl-auto=update` in dev, `validate` once stable). Flyway is intentionally deferred until Milestone 7 — avoiding migration churn while the domain model is actively evolving is the correct trade-off at this stage. Milestone 7 introduces a single consolidated baseline migration; from that point, all schema changes go through versioned migrations and `ddl-auto` is locked to `validate`.

**Engineering highlights:** request-scoped beans for session correlation, aggregate/projection separation, CQRS with void command dispatch.

### Milestone 1 — Structured Extraction + PII Redaction ✓ COMPLETE

**Objective:** turn the PDF into clean structured data.

**Deliverables:**

- Port `InvoiceFieldExtractor`.
  - Record `InvoiceFields`.
  - Adapter using LangChain4j `AiServices` + JSON Schema enforcement.
  - `PiiRedactor` applied before persisting.

**Dependencies:** Milestone 0.

**Engineering highlights:** typed extraction via LangChain4j AiServices with JSON Schema enforcement, GDPR-compliant PII redaction pipeline, retry with exponential backoff.

**Architectural decision — multi-model role routing (resolved):** two named beans `fastChatModel` and `smartChatModel` are defined in `ChatModelRolesConfig`. In dev both alias the single configured provider. In production they can be independently routed to different providers by replacing the aliases with provider-specific beans (`llm.role.fast.*` / `llm.role.smart.*` properties). Role assignment: `fastChatModel` → classification, PII redaction; `smartChatModel` → structured extraction, RAG, agent reasoning.

### Milestone 2 — Knowledge Base Ingestion + Hybrid Search + Market Price Consumer ← NEXT

**Objective:** populate the regulatory knowledge base, build the retrieval layer the assistant will use, and introduce event-driven market price ingestion via Kafka.

**Deliverables — Knowledge Base:**

- Migrate existing Milestone 1 string-literal prompts to versioned `.txt` files under `src/main/resources/prompts/` (one file per role: `classify.txt`, `extract-electricity.txt`, `extract-gas.txt`, `extract-water.txt`, `extract-telecom.txt`, `redact-pii.txt`). Loaded at startup via `ClassPathResource`; adapters inject the text, not the path. New prompts for Milestone 2 follow the same convention.
  - Initial golden set (5–10 anonymized invoices) with field-extraction tests as the baseline for the M6 eval harness.
  - Bounded context `knowledge/` with `KnowledgeDocument` aggregate and `KnowledgeChunk`.
  - Ingestion pipeline: fetch regulatory documents (CNMC methodology, REE/ESIOS tariff guides, BOE sector regulations) → chunk → embed → store in pgVector with metadata (`doc_type`, `source`, `title`, `section`).
  - Port `KnowledgeSearchRepository`.
  - Adapter combining pgVector + Postgres `tsvector` BM25 with Reciprocal Rank Fusion (RRF).
  - Metadata filters (`doc_type`, `supply_type`).
  - Optional cross-encoder re-ranker.
  - Admin endpoint `POST /api/v1/admin/knowledge/ingest` to trigger ingestion manually.
  - Retrieval tests with recall@k / MRR against a small curated query set.

**Deliverables — Market Price Consumer (Kafka):**

- Apache Kafka (KRaft, no ZooKeeper) added to `docker-compose.yml` under the `kafka` profile. Start with: `docker-compose --profile kafka up -d`.
  - `spring-kafka` dependency in `pom.xml`.
  - Bounded context `market/` with:
    - `MarketRate` aggregate: `id`, `supplyType` (`InvoiceType` enum), `company`, `tariffName`, `pricePerKwh`, `contractedPowerPrice` (nullable, electricity only), `validFrom`, `validTo`, `region`, `source`, `receivedAt`.
    - Port `MarketRateRepository`.
    - `market_rates` table (JPA entity).
  - `MarketPriceEvent` record (Kafka message schema, deserialized from JSON): `eventId`, `supplyType`, `company`, `tariffName`, `pricePerKwh`, `contractedPowerPrice` (nullable), `validFrom`, `validTo`, `region`, `source`, `publishedAt`.
  - `MarketPriceConsumer`: `@KafkaListener` on topic `market.price-updated`, consumer group `billmind-market`. Deserializes JSON → `MarketPriceEvent` → delegates to `SaveMarketRateUseCase`. Idempotent upsert on `(company, supplyType, tariffName, validFrom)`.
  - `SaveMarketRateUseCase` — application layer between consumer and repository (keeps hexagonal rules intact; the consumer lives in infrastructure and never touches the domain directly).
  - Dead-letter topic `market.price-updated.DLT` for deserialization or validation failures.
  - Integration test with TestContainers Kafka (`spring-kafka-test`).

**Dependencies:** Milestone 0 (database infrastructure).

**Note on the price producer:** BillMind only consumes. The service that fetches prices from external sources (e.g. REE ESIOS API) and publishes `MarketPriceEvent` is a separate system, developed independently. BillMind trusts its events without question.

**Candidate documents (knowledge base):**
- CNMC: tariff methodology circulars
  - REE: PVPC price guides, access toll documentation
  - BOE: Real Decreto 1164/2001, RD 216/2014, and related sector regulations
  - Glossary of billing terms (CUPS, término de potencia, peaje de acceso, etc.)

**Engineering highlights:** versioned prompt files loaded via `ClassPathResource`, initial extraction golden set as regression baseline, hybrid pgVector + BM25 retrieval with RRF, recall@k / MRR evaluation, metadata-filtered knowledge search; event-driven market price ingestion via Kafka (KRaft) with idempotent upsert, DLT for failures, and TestContainers integration tests.

### Milestone 3 — `assistant/` module — Conversational RAG

**Objective:** deliver conversational invoice analysis with SSE streaming responses and mandatory cited sources.

**Deliverables:**

- Bounded context `assistant/` with `Conversation`, `Message`, `Citation`.
  - `AskAssistantUseCase` with dual context strategy:
    - User's invoice full text → passed directly in the prompt (no retrieval needed, fits in context window).
    - Regulatory knowledge base → semantic RAG retrieval (CNMC, REE, BOE documents).
  - `POST /api/v1/assistant/chat` with SSE streaming.
  - Persistent conversational memory.
  - Mandatory citations (regulatory source + invoice line when applicable).
  - Prompt-injection guardrails on input.

**Dependencies:** Milestone 1 (structured invoice fields), Milestone 2 (knowledge base populated and searchable).

**Engineering highlights:** SSE streaming in Spring, dual-context (direct invoice text + regulatory RAG), mandatory citation enforcement at the API boundary, prompt-injection guardrails.

### Milestone 4 — Market Price Producer *(separate service, developed independently)*

**Objective:** build the external service that fetches prices from real sources and publishes `MarketPriceEvent` to Kafka, making BillMind's consumer fully operational end-to-end.

**Scope:** this is a standalone Spring Boot service, not part of the `billmind` repo. It shares the `market.price-updated` Kafka topic and the `MarketPriceEvent` JSON schema defined in Milestone 2.

**Deliverables:**

- Daily `@Scheduled` job pulling from REE ESIOS API for electricity; mocks for gas, water, telecom initially.
  - Maps API response → `MarketPriceEvent` and publishes to `market.price-updated`.
  - Idempotent: does not re-publish if price has not changed since last run.
  - Admin endpoint for manual trigger and last-run inspection.

**Dependencies:** Milestone 2 (Kafka infrastructure up, topic and event schema defined).

**Engineering highlights:** event publishing decouples data sourcing from BillMind's ingestion; any future price source (scraper, third-party API, manual upload) can replace or extend this producer without touching BillMind.

### Milestone 5 — `comparison/` module — Tool-calling Agent

**Objective:** deliver the comparison engine: a tool-calling agent that evaluates user rates against current market benchmarks and computes concrete savings estimates.

**Deliverables:**

- LangChain4j `AiServices` with `@Tool` methods: `getInvoiceFields`, `getCurrentMarketRates`, `getDatasetBenchmark`, `calculateAnnualSavings`.
  - `CompareInvoiceUseCase`.
  - `ComparisonResult` record.
  - `POST /api/v1/invoices/{id}/comparison`.
  - `comparisons` table.
  - Traces of which tools the agent invoked.

**Dependencies:** Milestones 1, 2 (`market_rates` table populated via Kafka consumer).

**Engineering highlights:** LangChain4j `@Tool` agent with deterministic Java tools for numerical math, tool invocation tracing, structured comparison output.

### Milestone 6 — Evaluation Harness + Observability

**Objective:** introduce rigorous quality measurement and production observability — the engineering standard that separates a working prototype from a deployable AI system.

**Deliverables:**

- `_shared/eval/` module with a ~50-example golden set.
  - RAGAS-style metrics: faithfulness, context precision, answer relevancy.
  - Regression test in `mvn verify` that fails if quality drops.
  - Self-hosted Langfuse with traces, tokens, latencies, estimated cost.
  - Basic dashboard.

**Dependencies:** Milestones 3, 5.

**Engineering highlights:** RAGAS-style metrics (faithfulness, context precision, answer relevancy), quality regression gate in CI, structured LLM observability with token and latency tracking.

### Milestone 7 — Production & Security (Phase 2)

**Objective:** make the project deployable and add user accounts.

**Deliverables:**

- Spring Security + JWT (env vars already in `.env.example`).
  - `users` table; nullable `user_id` migration on `sessions`.
  - Login that links the current anonymous session to the new user.
  - Rate limiting (per anonymous session and per user).
  - Semantic response cache.
  - PII redaction in logs.
  - Advanced prompt-injection defenses.
  - **Flyway** introduced now: consolidate the current schema into a baseline initial migration, switch `ddl-auto` to `validate`, and from this point all schema changes go through versioned migrations.

**Dependencies:** all previous.

**Engineering highlights:** production security hardening (JWT, rate limiting, advanced prompt-injection defenses, PII redaction in logs), semantic response cache, anonymous-to-authenticated session migration, consolidated Flyway baseline.

### Milestone 8 — Minimal Frontend *(optional)*

**Objective:** polished demo.

**Deliverables:**

- Next.js + Tailwind.
  - PDF upload, invoice listing, chat with streaming and clickable citations that open the PDF at the cited page.
  - Session UUID generated client-side and persisted in `localStorage`.
  - Public deploy (Docker compose, or Vercel + Fly.io with Ollama).

**Dependencies:** Milestone 3 minimum, ideally with 5.

**Engineering highlights:** Next.js + Tailwind with SSE streaming and citation-linked PDF viewer; session UUID persisted client-side; full-stack Docker Compose deployment with Ollama, PostgreSQL + pgVector, and the Spring Boot API behind a single command.
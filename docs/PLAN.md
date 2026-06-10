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
| Apache Kafka (KRaft) + `spring-kafka` | 2 | Event-driven market price ingestion — no polling |
| `market/` bounded context — `ElectricityRate` aggregate + `electricity_rates` table | 2 | Persist incoming price events for the comparison engine |
| `ElectricityPriceConsumer` — Kafka listener on `market.electricity-price-updated`; future `GasPriceConsumer`, etc. follow the same per-supply-type pattern | 2 | Prices arrive in real-time; the external producer is a separate service |
| `comparison/` bounded context — deterministic savings engine | 3 | Core product value: quantify overpayment immediately on upload |
| Comparison result embedded in `POST /api/v1/invoices` response | 3 | No extra step — savings visible the moment the invoice is processed |
| `comparisons` table | 3 | Persist results for history and future re-evaluation |
| `assistant/` bounded context | 5 | Explains the savings, answers follow-up questions |
| SSE streaming | 5 | Real LLM UX |
| Persistent conversational memory | 5 | Multi-turn history; conversation tied to session |
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

electricity_rates (id, supply_type, company, tariff_name,
              price_per_kwh,
              price_per_kwh_valle, price_per_kwh_llano, price_per_kwh_punta,
              contracted_power_price, contracted_power_price_p2,
              valid_from, valid_to, region, source, received_at)

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

### Milestone 2 — Knowledge Base Ingestion + Hybrid Search + Market Price Consumer ✓ COMPLETE

> **Electricity-first:** all milestones through Phase 1 target electricity invoices only (`InvoiceType.LUZ`). Uploading a gas, water, or telecom invoice returns HTTP 422 (`UnsupportedSupplyTypeException`). Support for other supply types is deferred to a later milestone.

**Objective:** populate the regulatory knowledge base, build the retrieval layer the assistant will use, and introduce event-driven market price ingestion via Kafka.

**Deliverables — Market Price Consumer (Kafka): ✓ COMPLETE**

- Apache Kafka (KRaft, no ZooKeeper) in `docker-compose.yml` under the `kafka` profile.
  - `spring-kafka` dependency in `pom.xml`.
  - Bounded context `market/` with:
    - `ElectricityRate` aggregate: `id`, `supplyType` (`InvoiceType` enum), `company`, `tariffName`, `pricePerKwh` (nullable for TOU tariffs), `pricePerKwhValle`, `pricePerKwhLlano`, `pricePerKwhPunta` (nullable for flat-rate tariffs), `contractedPowerPrice`, `contractedPowerPriceP2` (both nullable), `validFrom`, `validTo`, `region`, `source`, `receivedAt`.
    - Port `ElectricityRateRepository`.
    - `electricity_rates` table (JPA entity).
  - `ElectricityPriceEvent` record (Kafka message schema for electricity, deserialized from JSON): `eventId`, `company`, `tariffName`, `pricePerKwh` (nullable for TOU), `pricePerKwhValle`, `pricePerKwhLlano`, `pricePerKwhPunta` (nullable for flat-rate), `contractedPowerPrice`, `contractedPowerPriceP2` (both nullable), `validFrom`, `validTo`, `region`, `source`, `publishedAt`. A `type` discriminator field is required; unknown types are sent directly to DLT. Future supply types add their own event records (`GasPriceEvent`, etc.).
  - `ElectricityPriceConsumer`: `@KafkaListener` on topic `market.electricity-price-updated`, consumer group `billmind-market`. Deserializes JSON → `ElectricityPriceEvent` → delegates to `SaveElectricityRateUseCase`. Idempotent upsert on `(company, supplyType, tariffName, validFrom)`. Future supply types introduce their own consumer class (`GasPriceConsumer` on `market.gas-price-updated`, etc.).
  - `SaveElectricityRateUseCase` — application layer between consumer and repository (keeps hexagonal rules intact; the consumer lives in infrastructure and never touches the domain directly).
  - Dead-letter topic `market.electricity-price-updated.DLT` for deserialization or validation failures; domain validation errors go to `market.electricity-price-updated.domain-errors`.
  - Integration test with TestContainers Kafka (`spring-kafka-test`).

**Deliverables — Knowledge Base: ✓ COMPLETE**

- Bounded context `knowledge/` with `KnowledgeDocument` aggregate and `KnowledgeChunk`.
  - Ingestion pipeline: text → chunk (overlap sliding window, 150 words / 30 overlap) → embed (AllMiniLM-L6-v2, local ONNX, 384d) → store in pgVector with metadata (`doc_type`, `source`, `title`, `section`).
  - Port `KnowledgeSearchRepository`.
  - Adapter combining pgVector cosine similarity + Postgres `tsvector` BM25 (`unaccent` + `to_tsvector('spanish', ...)`) with Reciprocal Rank Fusion (RRF).
  - Admin endpoint `POST /api/v1/admin/knowledge/ingest` to trigger ingestion; `DELETE` to clear; `POST /reindex` to rebuild IVFFlat index.
  - Seed data: 6 regulatory documents (CNMC, REE/2.0TD, PVPC, glossary, invoice reading guide, FAQ) auto-loaded at startup when `knowledge.seed.enabled=true`.
  - Pluggable embedding provider via `EMBEDDING_PROVIDER` env var (`allminilm` default, `openai`, `ollama`).
  - IVFFlat index managed automatically by `JpaKnowledgeRepository.rebuildIndex()` (`lists = sqrt(rows)`; skipped below 100 vectors).
  - Retrieval quality IT: `HybridKnowledgeSearchRepositoryRetrievalIT` with 8-query golden set asserting recall@3 ≥ 0.625, recall@5 ≥ 0.750, MRR@5 ≥ 0.500.

**Dependencies:** Milestone 0 (database infrastructure).

**Note on the price producer:** BillMind only consumes. The service that fetches prices from external sources (e.g. REE ESIOS API) and publishes `ElectricityPriceEvent` is a separate system, developed independently. BillMind trusts its events without question.

**Engineering highlights:** event-driven market price ingestion via Kafka (KRaft) with flat-rate and TOU pricing support, idempotent upsert, DLT + domain-error topics, TestContainers integration tests; hybrid pgVector + BM25 retrieval with RRF, automated IVFFlat index lifecycle, recall@k / MRR quality gate in CI.

### Milestone 3 — `comparison/` module — Savings Engine ← NEXT

**Objective:** deliver the core product value: quantify exactly how much the user is overpaying and what they should switch to. The comparison result is returned synchronously with the invoice upload — no extra step required from the user.

**Product rationale:** the savings figure is the "wow" moment. Everything else (chat, regulatory context) exists to explain and deepen that finding. Showing it immediately on upload, before the user asks anything, is the right narrative order.

**Deliverables:**

- Bounded context `comparison/` fully implemented (currently skeleton only).
  - Domain: `ComparisonResult` value object — `userPricePerKwh`, `bestRate` (company, tariffName, pricePerKwh), `alternativeRates` (up to 3 ranked alternatives), `annualKwhEstimate`, `annualSavingsEuros`, `comparedAt`.
  - Port `ElectricityRateQueryPort` — queries `electricity_rates` for the best available rates for a given supply type.
  - `CompareInvoiceUseCase` — deterministic arithmetic, no LLM: finds cheapest flat-rate and TOU options in `electricity_rates`, estimates annual kWh from the billing period, computes savings vs user's extracted `pricePerKwh`. The LLM never does numerical math.
  - Graceful degradation: if `electricity_rates` is empty (producer not yet running), `ComparisonResult` is `null` — no error, no blocking.
  - `comparisons` table — stores each result with `invoiceId`, `sessionId`, result JSON, and `comparedAt`.
  - `POST /api/v1/invoices/{id}/comparison` — explicit re-run endpoint.
  - **Synchronous integration with upload:** `UploadInvoiceUseCase` calls `CompareInvoiceUseCase` after extraction; the `POST /api/v1/invoices` response includes a `comparison` field (nullable).
  - Static HTML updated: savings card displayed immediately after successful upload showing `userPricePerKwh`, `bestRate`, `annualSavingsEuros`. Hidden when `comparison` is null.

**Dependencies:** Milestone 1 (extracted invoice fields including `pricePerKwh` and `consumptionKwh`), Milestone 2 (`electricity_rates` table — data populated by Kafka producer when running).

**Note on the LangChain4j tool-calling agent:** the original design used `AiServices` with `@Tool` methods for the comparison. That approach is deferred — deterministic arithmetic is faster, cheaper, testable, and delivers the same value. The agent layer can be added later if reasoning over multiple tariff dimensions becomes complex.

**Engineering highlights:** deterministic savings calculation (arithmetic, not LLM), graceful null when market data is absent, synchronous result embedded in upload response, savings card in static HTML.

### Milestone 4 — Market Price Producer *(separate service, developed independently)*

**Objective:** build the external service that fetches prices from real sources and publishes per-supply-type price events to Kafka, making BillMind's consumer fully operational end-to-end.

**Scope:** this is a standalone Spring Boot service, not part of the `billmind` repo. It publishes to per-supply-type topics (`market.electricity-price-updated`, `market.gas-price-updated`, etc.) using the event schemas defined in Milestone 2 (`ElectricityPriceEvent`, and future `GasPriceEvent`, etc.).

**Deliverables:**

- Daily `@Scheduled` job pulling from REE ESIOS API for electricity; mocks for gas, water, telecom initially.
  - Maps API response → `ElectricityPriceEvent` (or the corresponding per-supply-type event) and publishes to the matching topic.
  - Idempotent: does not re-publish if price has not changed since last run.
  - Admin endpoint for manual trigger and last-run inspection.

**Dependencies:** Milestone 2 (Kafka infrastructure up, topic and event schema defined).

**Engineering highlights:** event publishing decouples data sourcing from BillMind's ingestion; any future price source (scraper, third-party API, manual upload) can replace or extend this producer without touching BillMind.

### Milestone 5 — `assistant/` module — Conversational RAG

**Objective:** deliver conversational invoice analysis — the layer that explains the comparison result and answers follow-up questions. By this point the user already knows their savings figure; the chat exists to help them understand why and how to act.

**Deliverables:**

- Bounded context `assistant/` fully implemented (currently: domain model + SSE streaming wired, `InMemoryAssistantRepository` — persistence missing).
  - `conversations` and `messages` tables; `JpaAssistantRepository` replacing `InMemoryAssistantRepository`.
  - `AskAssistantUseCase` with dual context strategy:
    - User's invoice full text → passed directly in the prompt (no retrieval needed, fits in context window).
    - Regulatory knowledge base → semantic RAG retrieval (CNMC, REE, BOE documents from Milestone 2).
  - `POST /api/v1/assistant/chat` with SSE streaming (already wired; needs persistent memory).
  - `GET /conversations/{id}` — returns conversation history; responds only if `X-Session-Id` matches.
  - Mandatory citations (regulatory source cited in every answer that references regulation).
  - Prompt-injection guardrails on input.
  - Static HTML chat page connected to the comparison result: pre-populated suggestion "¿Cómo cambio a [bestRate.tariffName] de [bestRate.company]?" using the comparison data from Milestone 3.

**Dependencies:** Milestone 1 (invoice fields), Milestone 2 (knowledge base), Milestone 3 (comparison result available as context seed for the first message).

**Engineering highlights:** persistent multi-turn conversation history, dual-context RAG (direct invoice text + regulatory retrieval), SSE streaming, citation enforcement, prompt-injection guardrails.

### Milestone 6 — Evaluation Harness + Observability

**Objective:** introduce rigorous quality measurement and production observability — the engineering standard that separates a working prototype from a deployable AI system.

**Deliverables:**

- `_shared/eval/` module with a ~50-example golden set.
  - RAGAS-style metrics: faithfulness, context precision, answer relevancy.
  - Regression test in `mvn verify` that fails if quality drops.
  - Self-hosted Langfuse with traces, tokens, latencies, estimated cost.
  - Basic dashboard.

**Dependencies:** Milestones 3, 5 (new numbering: comparison + chat).

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

**Dependencies:** Milestone 3 (comparison) minimum, ideally with Milestone 5 (chat).

**Engineering highlights:** Next.js + Tailwind with SSE streaming and citation-linked PDF viewer; session UUID persisted client-side; full-stack Docker Compose deployment with Ollama, PostgreSQL + pgVector, and the Spring Boot API behind a single command.
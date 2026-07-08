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

Visitors can register to get persistent history, multiple invoices over time, and personalized recommendations. **Identity is owned by an external user/identity microservice** — BillMind does not store credentials or manage accounts; it only links an existing anonymous session to the external `user_id` once the upstream service authenticates the visitor. This is the same delegation model already used for admin-route authentication today.

---

## 2. Stack — kept and added

**Kept:** Spring Boot 3.5.0, Java 21, LangChain4j 1.0.0, Ollama (local), PostgreSQL 16 + pgVector (IVFFlat, 384 dim), TestContainers. Hexagonal + DDD. CQRS with command bus. `GlobalExceptionHandler`.

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
| `assistant/` bounded context | 5 | Explains the savings, answers follow-up questions |
| SSE streaming + `conversationId` handshake | 5 | Real LLM UX with in-memory multi-turn |
| Eval harness + golden set + RAGAS-style metrics | 6 | Quality regression in CI |
| LLM tracing → external Langfuse (backend deployed as shared infra, referenced by env var) | 6 | LLM observability |
| Spring Security + delegated token validation (external identity service) + rate limiting | 7 | Phase 2 — no local user/credential storage |
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

knowledge_base (id, source, doc_type, title, url, valid_from, valid_to)
   └── vector_store (chunks + embedding + metadata
                    {doc_id, doc_type, source, section})

-- NOT persisted (Phase 1 decision):
-- comparisons  → recalculated on-the-fly from invoice fields + live electricity_rates
-- conversations → InMemoryAssistantRepository; Phase 1 is anonymous, no history UX needed
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

> **Electricity-first:** all milestones through Phase 1 target electricity invoices only (`SupplyDomain.ELECTRICITY`). Uploading a gas, water, or telecom invoice returns HTTP 422 (`UnsupportedSupplyTypeException`). Support for other supply types is deferred to a later milestone.

**Objective:** populate the regulatory knowledge base, build the retrieval layer the assistant will use, and introduce event-driven market price ingestion via Kafka.

**Deliverables — Market Price Consumer (Kafka): ✓ COMPLETE**

- Apache Kafka (KRaft, no ZooKeeper) in `docker-compose.yml` under the `kafka` profile.
  - `spring-kafka` dependency in `pom.xml`.
  - Bounded context `market/` with:
    - `ElectricityRate` aggregate: `id`, `supplyType` (`SupplyDomain` enum), `company`, `tariffName`, `pricePerKwh` (nullable for TOU tariffs), `pricePerKwhValle`, `pricePerKwhLlano`, `pricePerKwhPunta` (nullable for flat-rate tariffs), `contractedPowerPrice`, `contractedPowerPriceP2` (both nullable), `validFrom`, `validTo`, `region`, `source`, `receivedAt`.
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
  - Seed data: 5 regulatory documents (glossary, REE guide, CNMC circular, BOE regulation, general facturación/FAQ guide) auto-loaded at startup when `knowledge.seed.enabled=true`.
  - Pluggable embedding provider via `EMBEDDING_PROVIDER` env var (`allminilm` default, `openai`, `ollama`).
  - IVFFlat index managed automatically by `JpaKnowledgeRepository.rebuildIndex()` (`lists = sqrt(rows)`; skipped below 100 vectors).
  - Retrieval quality IT: `HybridKnowledgeSearchRepositoryRetrievalIT` with 8-query golden set asserting recall@3 ≥ 0.625, recall@5 ≥ 0.750, MRR@5 ≥ 0.500.

**Dependencies:** Milestone 0 (database infrastructure).

**Note on the price producer:** BillMind only consumes. The service that fetches prices from external sources (e.g. REE ESIOS API) and publishes `ElectricityPriceEvent` is a separate system, developed independently. BillMind trusts its events without question.

**Engineering highlights:** event-driven market price ingestion via Kafka (KRaft) with flat-rate and TOU pricing support, idempotent upsert, DLT + domain-error topics, TestContainers integration tests; hybrid pgVector + BM25 retrieval with RRF, automated IVFFlat index lifecycle, recall@k / MRR quality gate in CI.

### Milestone 3 — `comparison/` module — Savings Engine ✓ COMPLETE

**Objective:** deliver the core product value: quantify exactly how much the user is overpaying and what they should switch to. The comparison result is returned synchronously with the invoice upload — no extra step required from the user.

**Product rationale:** the savings figure is the "wow" moment. Everything else (chat, regulatory context) exists to explain and deepen that finding. Showing it immediately on upload, before the user asks anything, is the right narrative order.

**Deliverables:**

- Bounded context `comparison/` fully implemented.
  - Domain: `ComparisonResult` sealed interface → `ElectricityComparisonResult` record with `userPricePerKwh`, `userIsTou`, `annualKwhEstimate`, `flatBlock` and `touBlock` (`ElectricityOfferBlock`: bestCompany, bestTariffName, bestPricePerKwh, annualSavingsEuros, up to 3 alternatives).
  - Port `MarketOfferQueryPort` + `MarketOfferQueryAdapter` — queries `electricity_rates` and maps rows to `ElectricityMarketOffer`.
  - `ElectricityComparisonCalculator` — deterministic arithmetic, no LLM: finds cheapest flat-rate and TOU options, estimates annual kWh from the billing period, computes per-period TOU weighting from actual consumption when available (falls back to residential profile 30/40/30). The LLM never does numerical math.
  - `CompareInvoiceUseCase` — delegates to the calculator, returns `Optional<ComparisonResult>`.
  - Graceful degradation: if `electricity_rates` is empty (producer not yet running), `ComparisonResult` is `null` — no error, no blocking.
  - **No `comparisons` table** — results are recalculated on-the-fly from the persisted invoice fields + live `electricity_rates`. This keeps the schema minimal and always reflects the most current market data. Persisting comparison snapshots would add complexity with no user-facing benefit in Phase 1.
  - `GET /api/v1/invoices/{id}/comparison` — on-demand re-run endpoint (used when selecting an invoice from the list).
  - **Synchronous integration with upload:** `InvoiceController` calls `CompareInvoiceUseCase` after command dispatch; the `POST /api/v1/invoices` response includes a `comparison` field (nullable).
  - Static HTML: savings card rendered immediately after upload; animated counter; flat-rate and TOU blocks; hidden when `comparison` is null.

**Dependencies:** Milestone 1 (extracted invoice fields including `pricePerKwh` and `consumptionKwh`), Milestone 2 (`electricity_rates` table — data populated by Kafka producer when running).

**Engineering highlights:** deterministic savings calculation (arithmetic, not LLM), per-period TOU weighting with residential-profile fallback, graceful null when market data is absent, synchronous result embedded in upload response, animated savings card in static HTML.

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

### Milestone 5 — `assistant/` module — Conversational RAG ✓ COMPLETE

**Objective:** deliver conversational invoice analysis — the layer that explains the comparison result and answers follow-up questions. By this point the user already knows their savings figure; the chat exists to help them understand why and how to act.

**Deliverables:**

- Bounded context `assistant/` fully implemented.
  - Domain: `Conversation`, `ConversationMessage`, `MessageRole`, `ChatResult`, `RegulatorySnippet`. Ports: `AssistantRepository`, `AssistantLlmPort`, `InvoiceContextPort`, `RegulationSearchPort`.
  - `ChatUseCase` with dual context strategy:
    - User's invoice full text → passed in the system prompt (no retrieval needed, fits in context window).
    - Regulatory knowledge base → semantic RAG retrieval (CNMC, REE, BOE documents from Milestone 2).
  - Multi-turn conversation: `conversationId` returned as first SSE event; subsequent requests include it to continue the same thread. `ChatUseCase` finds the existing `Conversation` or creates a new one; history is passed to the LLM as alternating `UserMessage`/`AiMessage`.
  - **No DB persistence for conversations** — `InMemoryAssistantRepository` is sufficient for Phase 1 anonymous use. Conversations are scoped to the server process lifetime; refreshing the browser starts a fresh thread, which is acceptable without user accounts. DB persistence is a Phase 2 concern alongside JWT auth.
  - `POST /api/v1/assistant/chat` with SSE streaming: emits `conversation` (id), `token` (streamed text), `citations`, `[DONE]`.
  - Citations wired: `ChatResult` carries `List<ChatCitation>` from the retrieved regulatory snippets; rendered as chips in the UI.
  - Static HTML: full chat UI with invoice selector, suggestions, SSE token streaming, citation chips, comparison savings card.

**Dependencies:** Milestone 1 (invoice fields), Milestone 2 (knowledge base), Milestone 3 (comparison result shown alongside chat).

**Engineering highlights:** in-memory multi-turn conversation history, dual-context RAG (invoice text in system prompt + regulatory retrieval), SSE streaming with conversationId handshake, citation rendering.

### Post-Milestone 5 — Assistant context strategy (iterations 1–2) ✓ COMPLETE

**Objective:** evolve *how* the assistant gets its context. M5 shipped an eager strategy — every
question loaded invoice + all market rates + comparison + a RAG search into the prompt. Two
follow-up iterations fixed the resulting noise, cost and unreliable LLM ranking. Full rationale
in [`docs/ASSISTANT.md`](ASSISTANT.md).

**Iteration 1 — deterministic comparison in chat context ✓**
- The savings comparison is precomputed by the `comparison/` engine and surfaced to the assistant
  as a ready-made result (cheapest tariff, effective price, annual savings). The LLM **explains**
  it instead of ranking raw market rates itself — a task LLMs do unreliably.
- New port `ComparisonContextPort`; the eager prompt gained a `COMPARATIVA CALCULADA` section with
  rules telling the model to base "am I paying too much?" answers on it, not on the raw list.

**Iteration 2 — agentic tool calling ✓**
- **Why:** eager loading sent tariffs and regulation even when irrelevant (extra tokens/latency/cost
  every turn) and offered no directed querying (e.g. filter tariffs by company). Going agentic lets
  the LLM decide *what* to retrieve per question.
- **Design:** two mutually-exclusive `AssistantLlmPort` beans selected by `assistant.tools.enabled`
  (`@ConditionalOnProperty`). OFF (default) → `LlmAssistantAdapter` (eager, unchanged safety net for
  non-tool-capable models). ON → `AgenticAssistantLlmAdapter` (inlines only the invoice; the rest via
  tools). `ChatContextAssembler` skips the eager loads in tools mode (`ChatContext.invoiceOnly`).
- **Manual low-level tool loop** (not `AiServices`/`@Tool`): keeps `TimedChatLanguageModel`
  instrumentation, hexagonal ports and precise citation tracking. Bounded to 5 rounds with a final
  tool-less call to force a textual answer.
- **Three tools** over the existing ports: `get_invoice_comparison` (no params), `search_market_rates`
  (optional `company` filter, in-memory), `search_regulation` (required `query`). Shared Spanish
  formatting extracted to `AssistantContextFormatter`.
- **Precise citations:** only regulatory snippets actually retrieved by `search_regulation` this turn
  are cited — comparison/market-only turns return zero citations.
- **Flag OFF by default:** tool calling needs a tool-capable `smartChatModel` (cloud, or Groq
  `llama-3.3-70b-versatile`); small local Ollama is unreliable. Verified live against Groq across the
  three routing scenarios.

**Deferred (future work):**
- Within-turn tool-call **deduplication + short-circuit** — some models (observed with
  `llama-3.3-70b-versatile`) request an identical tool call twice before answering, wasting an LLM
  round and a redundant port call. Cache results by `(name, arguments)` and, when a round contains
  only already-seen calls, jump straight to the final tool-less answer.

**Dependencies:** Milestone 3 (comparison engine + `ComparisonContextPort`), Milestone 5 (assistant
port, conversation store, SSE).

### Milestone 6 — Evaluation Harness + Observability ✓ COMPLETE

**Objective:** introduce rigorous quality measurement and production observability — the engineering standard that separates a working prototype from a deployable AI system.

**Deliverables:**

- ✓ **RAGAS-style eval harness** (`src/test/java/.../eval/`, test scope) with a 50-example Spanish golden set (`src/test/resources/eval/rag_eval_dataset.json`). See `docs/EVAL.md`.
  - ✓ **Hybrid metrics:** deterministic layer (context precision as docType Average Precision, context recall hit@k, reference coverage via embedding cosine) that always gates CI without an LLM, plus an opt-in LLM-judge layer (faithfulness via claim verification, answer relevancy, fact coverage) enabled with `EVAL_LLM_ENABLED=true`.
  - ✓ Regression gate in `mvn verify` (`AssistantRagEvalIT`) — deterministic thresholds calibrated for AllMiniLM-L6-v2; pure metric unit tests in `RagasMetricsTest`.
  - ✓ Micrometer + Actuator (Prometheus) instrumentation on an isolated management port — see `docs/OBSERVABILITY.md`. *(Delivered ahead of this milestone.)*
  - ✓ Retrieval-only recall/MRR gate (`RagGoldenSetIT`, 30-question set) — predates this harness.
  - ✓ **LLM tracing (OpenTelemetry → Langfuse):** `TimedChatLanguageModel` now fans every call out to composable `LlmTelemetry` sinks. Two are wired, each behind its own flag: `MetricsLlmTelemetry` (`LLM_METRICS_ENABLED`, default on) publishes `llm.call.duration` / `llm.calls` / `llm.tokens` / `llm.cost.usd` to Actuator/Prometheus, and `TracingLlmTelemetry` (`LLM_TRACING_ENABLED`, default off) exports one OTLP span per call — with OpenTelemetry GenAI attributes (`gen_ai.*`) plus reused `ModelPricingRegistry` cost — to an external Langfuse backend at `{LANGFUSE_HOST}/api/public/otel/v1/traces`. The Langfuse **backend** stays shared infrastructure referenced only by env var (keys injected as secrets); the vendor-neutral OTLP export keeps it swappable. `LlmTracingConfig` builds the OTel SDK only when tracing is enabled and fails fast on a blank host. See `docs/OBSERVABILITY.md`.

**Dependencies:** Milestones 3, 5 (new numbering: comparison + chat).

**Engineering highlights:** hybrid RAGAS-style metrics (deterministic embedding gate always green in CI + opt-in LLM-as-judge faithfulness), quality regression gate in CI, structured LLM observability with token and latency tracking.

### Milestone 7 — Production & Security (Phase 2)

**Objective:** make the project deployable and connect it to authenticated user identity. **User accounts are not built in BillMind** — identity, registration, login, credential storage and token issuance are fully delegated to an external user/identity microservice, exactly as admin-route authentication already works today (`JwtAuthFilter` + `AdminRoutesService`). Milestone 7 extends that same delegation model to user-facing endpoints.

**Deliverables:**

- **Identity fully delegated to the external user microservice** (the model already in place for admin routes via `JwtAuthFilter` / `AdminRoutesService`). BillMind never stores credentials, never manages registration/login, and never issues or validates tokens itself — it trusts the identity asserted by the upstream service/gateway. Milestone 7 only extends the existing delegated-validation pattern to authenticated user-facing endpoints.
  - **No `users` table in BillMind.** `sessions` gains a nullable `user_id` column holding the opaque external user identifier — a foreign reference owned by the identity service, not a locally owned entity.
  - Session linking: when an authenticated request arrives, BillMind associates the current anonymous session with the external `user_id` carried in the validated token. The registration and login flows themselves live entirely in the external service.
  - Extend `JwtAuthFilter` / `AdminRoutesService` to cover authenticated user endpoints (same delegated-validation pattern as admin routes).
  - Rate limiting (per anonymous session and per user).
  - Semantic response cache.
  - PII redaction in logs.
  - Advanced prompt-injection defenses.
  - **Flyway** introduced now: consolidate the current schema into a baseline initial migration, switch `ddl-auto` to `validate`, and from this point all schema changes go through versioned migrations.

**Dependencies:** all previous.

**Engineering highlights:** identity fully delegated to an external user microservice (no local credential/user storage), production security hardening (delegated token validation, rate limiting, advanced prompt-injection defenses, PII redaction in logs), semantic response cache, anonymous-to-external-user session linking, consolidated Flyway baseline.

### Milestone 8 — Minimal Frontend *(optional)*

**Objective:** polished demo.

**Deliverables:**

- Next.js + Tailwind.
  - PDF upload, invoice listing, chat with streaming and clickable citations that open the PDF at the cited page.
  - Session UUID generated client-side and persisted in `localStorage`.
  - Public deploy (Docker compose, or Vercel + Fly.io with Ollama).

**Dependencies:** Milestone 3 (comparison) minimum, ideally with Milestone 5 (chat).

**Engineering highlights:** Next.js + Tailwind with SSE streaming and citation-linked PDF viewer; session UUID persisted client-side; full-stack Docker Compose deployment with Ollama, PostgreSQL + pgVector, and the Spring Boot API behind a single command.
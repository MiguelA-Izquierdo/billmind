# BillMind — Roadmap

Long-term plan for evolving BillMind from its current state (PDF ingestion + classification + vectorization) into a complete RAG + LLM system. Living reference; updated when scope changes.

---

## 1. Product Vision

### Phase 1 — Anonymous (scope of this plan)

A visitor opens the app, uploads a utility invoice PDF, and the system:

- Extracts structured fields (price/kWh, contracted power, billing period, totals).
- Compares the invoice against (a) current market rate data and (b) an aggregated anonymous corpus of past invoices.
- Lets the user **chat** about their invoice with citations to specific chunks.

No login. The frontend generates a UUID per visitor and sends it as `X-Session-Id`. The backend correlates resources (uploaded invoices, conversations) to that UUID without authenticating it.

### Phase 2 — Authenticated (post-Milestone 7)

Visitors can register to get persistent history, multiple invoices over time, and personalized recommendations. Anonymous historical invoices remain in the dataset; existing sessions can be attached to a newly created user account.

---

## 2. Stack — kept and added

**Kept:** Spring Boot 3.5.0, Java 21, LangChain4j 0.36.2, Ollama (local), PostgreSQL 16 + pgVector (HNSW, 384 dim), TestContainers. Hexagonal + DDD. CQRS with command bus. Domain events. `GlobalExceptionHandler`.

**Added per milestone:**

| Addition | Milestone | Why |
|---|---|---|
| Enriched `Invoice` aggregate + table | 0 | Today the classifier metadata is discarded |
| `sessions` table + `SessionContext` + `X-Session-Id` filter | 0 | Anonymous correlator, FK targets |
| LangChain4j `AiServices` + JSON Schema structured outputs | 1 | Move from string parsing to typed extraction |
| `PiiRedactor` | 1 | GDPR + dataset safety |
| Hybrid retrieval (vector + `tsvector` BM25 + RRF) | 2 | Naïve vector RAG fails on numbers/dates |
| Cross-encoder re-ranker | 2 | Retrieval quality |
| `assistant/` bounded context | 3 | Conversational RAG — the demo "wow" |
| SSE streaming | 3 | Real LLM UX |
| Persistent conversational memory | 3 | Stateless design — no volatile in-process storage |
| `@Scheduled` market sync (e.g. ESIOS API) | 4 | Feeds the comparison agent |
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
   │      │
   │      └── vector_store (chunks + embedding + metadata
   │                       {invoice_id, supply_type, provider, page})
   │
   └── conversations (id, session_id FK, created_at)
          └── messages (id, conversation_id FK, role, content,
                       citations[], created_at, tokens_in, tokens_out)

market_rates (id, supply_type, provider, tariff_name,
              price_per_kwh, valid_from, valid_to, region, source)

comparisons (id, invoice_id FK, session_id FK, result_json,
             tools_called[], created_at, model_used)

eval_runs (id, golden_set_version, faithfulness, context_precision,
           answer_relevancy, created_at)  -- Milestone 6
```

`invoices` and chunks contain **no PII** — redaction happens at extraction time (Milestone 1).

---

## 4. Cross-cutting Considerations

1. **Dataset anonymity.** The corpus is a shared aggregate. `session_id` is traceability, not authorization.
2. **Mandatory PII redaction** before persisting any invoice or chunk. Tested explicitly.
3. **Minimal authorization for conversations.** A `GET /conversations/{id}` only responds if the request `X-Session-Id` matches the row. This is the only "auth" in Phase 1.
4. **Determinism where possible.** The LLM reasons; numerical math (annual savings) lives in deterministic Java tools. Never ask the LLM to multiply.
5. **Mandatory citations.** Every assistant reply cites the `InvoiceChunk`s it used. No citation, no render.
6. **Cost / latency awareness.** Token budgets. Small models for classification; larger for reasoning.
7. **Prompt injection defense.** User input is never concatenated into the system prompt. Sandwich pattern: instructions → delimited data → instructions.
8. **Evaluation as a first-class citizen.** Golden set grows with each milestone. CI fails on quality regression.
9. **Observability from Milestone 6.** Structured traces, tokens, latencies, estimated cost per session.
10. **Hexagonal rules untouchable.** No Spring/LangChain4j imports in `domain/`. Ever.

---

## 5. Milestones

> Each milestone is independently shippable and demoable.

### Milestone 0 — Foundations

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

### Milestone 1 — Structured Extraction + PII Redaction

**Objective:** turn the PDF into clean structured data.

**Deliverables:**

- Port `InvoiceFieldExtractor`.
- Record `InvoiceFields`.
- Adapter using LangChain4j `AiServices` + JSON Schema enforcement.
- `PiiRedactor` applied before persisting.
- Versioned prompts in `infrastructure/ai/prompts/` (English instructions, "respond in Spanish" tail).
- Initial golden set (5-10 anonymized invoices) with extraction tests.

**Dependencies:** Milestone 0.

**Engineering highlights:** typed extraction via LangChain4j AiServices with JSON Schema enforcement, GDPR-compliant PII redaction pipeline, versioned prompt management, retry with exponential backoff.

### Milestone 2 — Hybrid Search

**Objective:** retrieval that actually works for invoice content.

**Deliverables:**

- Port `InvoiceSearchRepository`.
- Adapter combining pgVector + Postgres `tsvector` BM25 with Reciprocal Rank Fusion.
- Metadata filters (supply type, provider, period).
- "Current session" filter for queries about the just-uploaded invoice.
- Optional cross-encoder re-ranker.
- Retrieval tests with recall@k / MRR.

**Dependencies:** Milestone 1 (chunks need rich metadata).

**Engineering highlights:** hybrid pgVector + BM25 retrieval with Reciprocal Rank Fusion, recall@k / MRR evaluation, metadata-filtered vector search.

### Milestone 3 — `assistant/` module — Conversational RAG

**Objective:** deliver conversational invoice analysis with SSE streaming responses and mandatory cited sources.

**Deliverables:**

- Bounded context `assistant/` with `Conversation`, `Message`, `Citation`.
- `AskAssistantUseCase` with dual retriever (session + dataset).
- `POST /api/v1/assistant/chat` with SSE streaming.
- Persistent conversational memory.
- Mandatory citations.
- Prompt-injection guardrails on input.

**Dependencies:** Milestones 1, 2.

**Engineering highlights:** SSE streaming in Spring, dual-retriever (session + dataset), mandatory citation enforcement at the API boundary, prompt-injection guardrails.

### Milestone 4 — `market/` module

**Objective:** market data so `comparison/` is comparing against something real.

**Deliverables:**

- `market_rates` table.
- Daily `@Scheduled` job pulling from a real source (REE ESIOS API for electricity; mocks for gas/water/telecom initially).
- `MarketRateRepository`.
- Admin endpoint for inspection.

**Dependencies:** Milestone 0.

**Engineering highlights:** idempotent scheduled ETL, external API integration (REE ESIOS), rate-aware ingestion with conflict resolution.

### Milestone 5 — `comparison/` module — Tool-calling Agent

**Objective:** deliver the comparison engine: a tool-calling agent that evaluates user rates against current market benchmarks and computes concrete savings estimates.

**Deliverables:**

- LangChain4j `AiServices` with `@Tool` methods: `getInvoiceFields`, `getCurrentMarketRates`, `getDatasetBenchmark`, `calculateAnnualSavings`.
- `CompareInvoiceUseCase`.
- `ComparisonResult` record.
- `POST /api/v1/invoices/{id}/comparison`.
- `comparisons` table.
- Traces of which tools the agent invoked.

**Dependencies:** Milestones 1, 4.

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
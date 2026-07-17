# Architecture & Design Decisions — BillMind

Reference for sessions where modules are designed or extended. Use with `@CLAUDE.md` for conventions and agent instructions. For the milestone roadmap, see `@docs/PLAN.md`.

---

## Design Decisions

1. **UUID generated in the controller** — strict CQRS: commands return no value (`CommandBus.dispatch()` returns `void`).
2. **Pluggable embedding model** — selected at runtime via `EMBEDDING_PROVIDER` (`allminilm` default). `allminilm` runs as local ONNX inside the JVM (384d, no network call). `ollama` delegates to a local Ollama server (e.g. `bge-m3` at 1024d for better Spanish). `openai` uses the Embeddings API. Changing the model requires dropping `vector_store` and re-ingesting — the pgVector column dimension is a schema constraint.
3. **Automatic IVFFlat index management** — `PgVectorEmbeddingStore` is always configured with `useIndex=false`; the app manages the index lifecycle itself via `JpaKnowledgeRepository.rebuildIndex()`. At startup (after seed) and on `POST /admin/knowledge/reindex`: if vectors < 100 → no index (sequential scan); if vectors ≥ 100 → `DROP + CREATE INDEX` with `lists = sqrt(rows)`. This keeps `lists` correct as the dataset grows without any manual env var tuning. HNSW is the target for a future upgrade when available in `langchain4j-pgvector`.
4. **Chunk size 150, overlap 30** — tuned to AllMiniLM-L6-v2's 256-token limit. Configurable via `KNOWLEDGE_CHUNK_SIZE` and `KNOWLEDGE_CHUNK_OVERLAP`.
5. **Pluggable LLM provider** — selected at runtime via `LLM_PROVIDER` env var (`ollama` default). Ollama keeps all data local; cloud providers (OpenAI, Anthropic, Gemini, Groq) are available for environments where external API calls are acceptable. See *LLM Provider Strategy* section below.
6. **Domain Events** — cross-context reactions travel through `DomainEventPublisher`, a **synchronous in-process bus** (`SpringDomainEventPublisher`, a hand-rolled `Map<event class → handlers>` — **not** Spring's `ApplicationEventPublisher`). Producers publish **after** the persistence step (effectively after-commit) and the emitting use case is never wrapped in a wide `@Transactional`. The first consumer is the `metrics/` bounded context. See the *Domain Events* section below for the event catalogue, and `docs/PLAN.md` (principle #11) for the sync/after-commit rationale and the outbox upgrade path. The comparison, by contrast, is computed synchronously via the query bus, not event-driven.
7. **Frontend-generated session UUID** — sent as `X-Session-Id` header. The backend correlates resources to it but does not authenticate (Phase 1 is anonymous). Auth lands in Milestone 9.
8. **Admin route protection via external auth microservice** — admin operations (the whole `/api/v1/admin/**` tree, plus `DELETE /api/v1/market-rates`) delegate token validation to an external user service via `ExternalAuthPort` / `ExternalAuthAdapter`: the adapter calls `GET <AUTH_EXTERNAL_URL>/introspect` forwarding the `Authorization: Bearer <token>` header as-is; a 200 response means the token is valid; 401/403 and any I/O error fail closed (the adapter returns `false`). The boundary between BillMind and the auth service is the port — swapping the external service requires only a new adapter, not changes to the filter or domain.
9. **Authenticate in the filter, authorize in the engine.** `JwtAuthFilter` establishes an identity and nothing else; the access decision belongs to Spring Security's `AuthorizationFilter` (fed by `RouteAccessPolicy`) and, one layer deeper, to `@PreAuthorize` on each admin handler. A route is never open merely because a filter chose not to run — see *Admin route protection* below for what each layer catches.
10. **`RouteAccessPolicy` — one classifier, every guard.** Authentication and session correlation are independent axes, so every route resolves to exactly one `RouteAccess`: `ADMIN` (bearer token, no session), `ANONYMOUS` (session, no token), or `OPEN` (neither). `SessionFilter` acts on `ANONYMOUS`, the authorization manager enforces `ADMIN`, and the rate limiter derives its profile from the same classification — so a route can never be registered with one guard and forgotten by another. Anything unrecognized under `/api/v1/` defaults to `ANONYMOUS`, and anything under `/api/v1/admin/**` is `ADMIN` by convention — a new admin endpoint is guarded the moment it is mapped, without touching the policy. The policy matches on the decoded `RequestPath` the `DispatcherServlet` routes on, never the raw URI: a guard that disagrees with the router is one that can be walked around.
11. **PII redaction before persistence** — the aggregated invoice corpus is a product moat and must be safe by design. IBAN, DNI, postal address, full name, and phone are replaced with placeholders before storing.
12. **English system prompts with "respond in Spanish" instruction** — small Ollama models follow English instructions more reliably; output language is controlled by an explicit instruction at the end of the prompt.
13. **Flyway owns the relational schema; Hibernate only validates.** The schema previously grew under `ddl-auto=update`; it is now migration-driven from `src/main/resources/db/migration/` (`V1__baseline.sql`), and the default `ddl-auto` is `validate` — Hibernate asserts the mappings match the migrated schema and never mutates it. The V1 DDL was generated *from* the JPA entities against `pgvector:pg16`, so it stays byte-compatible with `validate` (e.g. `Instant → timestamp(6) with time zone`). Adoption on an existing database is handled by `baseline-on-migrate=true` + `baseline-version=0`: a database still carrying the old Hibernate-created tables is stamped at version 0, then V1 runs above it as an idempotent no-op (`CREATE ... IF NOT EXISTS`); a fresh database gets the tables created by V1. **The `vector_store` table and its IVFFlat index are deliberately *not* under Flyway** — they remain owned by `PgVectorEmbeddingStore` + `JpaKnowledgeRepository.rebuildIndex()` (Design Decision #3), because the vector column dimension and index lifecycle are runtime-configurable, not fixed schema. Flyway is disabled in the test profile: integration tests build a throwaway schema with `ddl-auto=create-drop`.

---

## Domain Events

Cross-context reactions travel through `DomainEventPublisher`. The implementation (`SpringDomainEventPublisher`) is a **synchronous, in-process bus**: a hand-rolled `Map<event class → List<DomainEventHandler>>`, built by collecting every `DomainEventHandler` bean at startup and routing each event to the handlers registered for its **exact** class. It does **not** use Spring's `ApplicationEventPublisher`, `@EventListener`, or `@Async`.

**Execution model:**

- **Synchronous, same thread, blocking.** `publish()` invokes each handler inline; a slow or throwing handler affects the producer's flow.
- **After-commit by construction.** Producers publish *after* the persistence step returns. Since `upload()` / the emitting use case is deliberately **not** wrapped in a wide `@Transactional` (the LLM calls would hold a DB connection open for seconds), the narrow Spring Data transaction has already committed by the time the event is published — effectively after-commit without `@TransactionalEventListener`.
- **PII-safe payloads.** Every payload carries only ids / enums / counters — never invoice or message text — so consumers in other contexts (e.g. `metrics/`) stay PII-free. A handler that needs more re-loads the aggregate by id.
- **Known gap:** a crash between commit and in-process dispatch loses the event — harmless for the current metrics/log consumers. The upgrade path (transactional outbox) and its trigger conditions are documented in `docs/PLAN.md` (principle #11).

### Event catalogue

| Event (`eventName`) | Producer | Emitted when | Payload (ids / enums / counters only) | Consumer(s) |
|---|---|---|---|---|
| `invoice.ingested` | `UploadInvoiceUseCase` | Invoice classified, PII-redacted, fields extracted and persisted OK | `invoiceId, sessionId, supplyType, provider, uploadedAt` | `InvoiceIngestedMetricsHandler` |
| `invoice.rejected` | `UploadInvoiceUseCase` | Upload rejected (not a supply invoice / unsupported supply type) | `invoiceId, sessionId, type, reason` | `InvoiceRejectedMetricsHandler` |
| `assistant.question-answered` | `ChatUseCase` | Assistant produced a complete answer | `conversationId, sessionId, invoiceId, questionLength, citationCount` | `AssistantQuestionAnsweredMetricsHandler` |

`citationCount == 0` on `assistant.question-answered` doubles as a knowledge-base coverage-gap signal (the answer cited no regulatory document).

> **Terminology:** these in-process **domain events** are distinct from the **Kafka market price events** (`ElectricityPriceEvent` on `market.electricity-price-updated`) that the `market/` context consumes. Both are called "events" but they are different mechanisms — the former is a synchronous in-monolith bus, the latter an inter-service message broker.

### Adding a domain event

1. Define the event in the producer context's `domain/event/` — a `final` class extending `BaseDomainEvent<Payload>`, with a `Payload` record carrying **only** non-PII ids/enums/counters.
2. Publish it from the application layer (use case) **after** persistence, via the injected `DomainEventPublisher`. Never wrap the emitting use case in a wide `@Transactional`.
3. Add a `DomainEventHandler<YourEvent>` `@Component` in the consuming context's `infrastructure/event/`. Wiring is automatic — `SpringDomainEventPublisher` picks up every handler bean at startup.
4. Update the event catalogue table above.

---

## Adding a new module (e.g. `comparison/`)

1. Create the structure: `domain/model/`, `domain/port/`, `application/usecase/`, `infrastructure/`.
2. The domain layer imports nothing from Spring or LangChain4j.
3. Spring beans live in `infrastructure/config/`.
4. Follow the exact pattern of `invoice/`.

## Adding the regulatory knowledge base (Milestone 2)

Invoice text is **not** vectorized. The pgVector store holds the regulatory knowledge base only (CNMC circulars, REE/ESIOS guides, BOE regulations). The chat assistant uses dual context: the user's full invoice text passed directly in the prompt + semantic retrieval from the knowledge base for regulatory questions.

- New bounded context `knowledge/` with `KnowledgeDocument` aggregate and `KnowledgeChunk`.
- Port `KnowledgeSearchRepository` in `knowledge/domain/port/`.
- Adapter combining pgVector cosine similarity + Postgres `tsvector` BM25 with Reciprocal Rank Fusion in `knowledge/infrastructure/adapter/`.
- LLM prompts live in `infrastructure/ai/prompts/` (never in the domain layer).

## LLM Provider Strategy

The `ChatModel` bean is selected at startup via `LLM_PROVIDER`. All providers implement the same LangChain4j `ChatModel` interface — no application code changes when switching.

| `LLM_PROVIDER` | Provider | Required env vars | Default model |
|---|---|---|---|
| `ollama` *(default)* | Local Ollama | `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL` | `llama3.2` |
| `openai` | OpenAI | `OPENAI_API_KEY` | `gpt-4o` |
| `anthropic` | Anthropic | `ANTHROPIC_API_KEY` | `claude-sonnet-4-6` |
| `gemini` | Google AI Studio (OpenAI-compatible) | `GEMINI_API_KEY` | `gemini-2.5-flash` |
| `groq` | Groq (OpenAI-compatible) | `GROQ_API_KEY` | `llama-3.3-70b-versatile` |

Gemini and Groq use Google's / Groq's OpenAI-compatible REST endpoints via `OpenAiChatModel` — no extra LangChain4j SDK dependency is required for either.

**The embedding model is selected via `EMBEDDING_PROVIDER`.** Changing it requires dropping `vector_store` and re-ingesting all documents — the pgVector column dimension is a schema constraint validated at startup.

**Security note:** when using a cloud provider, invoice text is sent to a third-party API. PII redaction (rule #7 in CLAUDE.md) must have run before any LLM call.

**Adding a new provider:**
1. If the provider has a native LangChain4j integration, add the `langchain4j-<provider>` dependency to `pom.xml`. If it exposes an OpenAI-compatible API, skip this step — reuse `langchain4j-openai` via `OpenAiChatModel.builder().baseUrl(...)`.
2. Add a `@Configuration @ConditionalOnProperty(name = "llm.provider", havingValue = "<provider>")` class under `invoice/infrastructure/config/chat/`.
3. Add the `llm.<provider>.*` properties to `application.properties`.
4. Add the API key check to `StartupReadinessChecker`.
5. Update this table.

---

## Admin route protection (implemented — external auth delegation)

BillMind **never validates tokens itself**. All authentication is delegated to an external user microservice via `ExternalAuthPort`. This applies to admin routes today and will extend to all authenticated endpoints in Milestone 9.

### Authenticating and authorizing are separate steps

A filter that both decides *and* enforces is a single point of failure: `SecurityConfig` used to say `anyRequest().permitAll()`, which idles Spring's authorization engine and leaves the real decision to `JwtAuthFilter.shouldNotFilter()`. One bug in path matching there — and the encoded-path bypass that `AdminRouteGuardBypassTest` pins was exactly that bug — opens `/api/v1/admin/**` with nothing behind it. So the two concerns are now split, and the guard is three layers deep, each covering a failure the others cannot:

| Layer | What it does | The failure it catches |
|---|---|---|
| `JwtAuthFilter` | **Authenticates only.** Never rejects, never decides. Puts an `ExternalTokenAuthentication` in the `SecurityContext`. | — (it is no longer a guard) |
| `RouteAccessAuthorizationManager` (via `anyRequest().access(...)`) | The access **decision**, taken by Spring's `AuthorizationFilter` from `RouteAccessPolicy`. | Wiring: a filter unregistered, the chain reordered, `shouldNotFilter` wrong. |
| `@PreAuthorize("hasRole('ADMIN')")` on each admin handler | The same requirement, bound to the method the dispatcher actually invokes. | A misclassification by `RouteAccessPolicy` itself — which the layer above, trusting that same policy, would wave through. |

```
RateLimitFilter        ← IP / session layers (pre-auth)
JwtAuthFilter          ← only when RouteAccessPolicy says ADMIN *and* a Bearer is present
  └─ ExternalAuthAdapter.isAuthorized(bearerToken)
       └─ GET <AUTH_EXTERNAL_URL>/introspect   (Authorization: Bearer <token>)
            200 → true   → SecurityContext = ExternalTokenAuthentication.authorized  (ROLE_ADMIN)
            4xx / error  → false → SecurityContext = ExternalTokenAuthentication.rejected (no authority)
PostAuthRateLimitFilter ← token layer, keyed on the *validated* identity
SessionFilter          ← only when RouteAccessPolicy says ANONYMOUS; requires a valid X-Session-Id UUID
AuthorizationFilter    ← the decision: RouteAccessAuthorizationManager
Controller             ← @PreAuthorize on admin handlers
```

The policy driving *authentication* is safe in a way that policy driving *authorization* was not: a route it misclassifies is merely left unauthenticated, and the layers below deny it. Skipping non-admin routes also keeps an anonymous caller from turning the public API into an amplifier against the auth service.

Denials never reach a controller's `try/catch`: `AuthorizationFilter` and `@PreAuthorize` both raise an `AccessDeniedException` that `ExceptionTranslationFilter` hands to `ApiSecurityErrorHandler` — **401** when the caller is anonymous (no token), **403** when a known caller is unauthorized (token rejected by introspection). `GlobalExceptionHandler` rethrows `AccessDeniedException` instead of answering it, so its `Exception` catch-all cannot swallow a 403 into a 500.

| Route | `RouteAccess` | Bearer token | `X-Session-Id` |
|---|---|---|---|
| `/api/v1/admin/**`, `DELETE /api/v1/market-rates` | `ADMIN` | required | — |
| `POST /api/v1/invoices`, `POST /api/v1/assistant/chat`, … | `ANONYMOUS` | — | required |
| `GET /api/v1/market-rates`, `/actuator/**`, `/chat/**` | `OPEN` | — | — |

Key files:

| File | Role |
|---|---|
| `_shared/domain/port/ExternalAuthPort` | Port — `isAuthorized(String bearerToken): boolean` |
| `_shared/infrastructure/adapter/ExternalAuthAdapter` | Adapter — `GET /introspect`, fail-closed on any error |
| `_shared/infrastructure/auth/JwtAuthFilter` | `OncePerRequestFilter` — extracts Bearer, calls port, populates the `SecurityContext`. Rejects nothing |
| `_shared/infrastructure/auth/ExternalTokenAuthentication` | The identity: principal is a SHA-256 fingerprint of the token, never the token (rule #6). `ROLE_ADMIN` only when introspection accepted it |
| `_shared/infrastructure/auth/ApiSecurityErrorHandler` | `AuthenticationEntryPoint` + `AccessDeniedHandler` — renders 401/403 in the API envelope, in Spanish |
| `_shared/infrastructure/route/RouteAccessPolicy` | Classifies every route as `ADMIN` / `ANONYMOUS` / `OPEN` |
| `_shared/infrastructure/route/RouteAccessAuthorizationManager` | Feeds that classification to Spring's authorization engine |
| `_shared/infrastructure/config/SecurityConfig` | `@EnableMethodSecurity`, `anyRequest().access(...)`, filter order |

Config: `AUTH_EXTERNAL_URL` env var → `app.auth.external-url`.

## Adding user authentication (Milestone 9)

Authentication remains delegated to the external microservice — no local token validation is added to BillMind. The work for Milestone 9 is:

- Widen `JwtAuthFilter` to authenticate user-facing endpoints too (today it skips every non-`ADMIN` route so an anonymous caller cannot force introspection calls). The authorization side needs no new filter: add a `RouteAccess.AUTHENTICATED` and let `RouteAccessAuthorizationManager` require an authenticated principal for it.
- `ExternalAuthAdapter.isAuthorized` can be enriched to return the resolved subject/roles from the `/introspect` response if downstream logic needs them (e.g. scoping invoice queries to the authenticated user instead of the session UUID). `ExternalTokenAuthentication` would then carry the real authorities instead of a single `ROLE_ADMIN`, and `TokenKeyGenerator` would key the post-auth rate limit by `userId` instead of the token fingerprint.
- Migration: add nullable `user_id` to `sessions`; on login, link the current anonymous session to the new user. Anonymous historical invoices remain part of the dataset.
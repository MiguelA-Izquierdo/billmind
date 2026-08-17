# CLAUDE.md — BillMind

> Developer tooling configuration for [Claude Code](https://docs.anthropic.com/en/docs/claude-code). Analogous to `.editorconfig` for LLM-assisted development — not part of the application runtime. For architecture decisions and extension points, see `@docs/ARCHITECTURE.md`. For the long-term roadmap, see `@docs/PLAN.md`.

---

## Project Description

**BillMind** is a Spring Boot 3.5.0 + Java 21 + LangChain4j 1.0.0 REST API. It ingests utility invoice PDFs, extracts structured fields, and stores them in PostgreSQL 16. A regulatory knowledge base (CNMC, REE, BOE documents) is stored in pgVector (IVFFlat, 384 dim — HNSW not yet in `langchain4j-pgvector:1.0.0-beta5`) with AllMiniLM-L6-v2 embeddings (local ONNX) and used for RAG in the chat assistant.

The product lets an anonymous visitor upload an invoice and (a) compare it against current market data and (b) chat about it. The chat uses dual context: the invoice's extracted structured fields passed directly to the LLM + semantic retrieval from the regulatory knowledge base. Only the structured fields (`InvoiceFields`) reach the assistant — `rawTextRedacted` (the full PII-redacted invoice text) is persisted but is **not** passed to the LLM. Because extraction keeps a fixed subset (see `ElectricityFields`), line items outside it — the electricity tax, meter rental, discounts — live only in `rawTextRedacted`. In **agentic mode** (`ASSISTANT_TOOLS_ENABLED=true`) the assistant reaches them through the `search_invoice_text` tool, which keyword-searches that text and returns only the matching lines, so the document is never inlined and a large PDF costs no more context than a small one (see `docs/ASSISTANT.md`). With tools off they remain unavailable. The power term is the exception and is now extracted as a *number* (`powerPriceP1PerKwDay` / `powerPriceP2PerKwDay`, €/kW/día): the comparison engine needs it to contrast against `contractedPowerPrice`, which a text fragment cannot give it. Being extracted, it also reaches the assistant in the context block next to the contracted kW, so "¿cuánto me cobran por la potencia?" no longer depends on the tool. Widening the rest of the field set is still pending; see `docs/PLAN.md`. User accounts are out of scope for Phase 1 — the frontend generates a UUID per session and sends it as `X-Session-Id`. Auth lands in Milestone 9 (Phase 2).

**Current state:** Milestones 0–7 are complete. Next up is Milestone 8 (optional Next.js frontend) or Milestone 9 (delegated user identity, which unblocks Milestone 10's `metrics/` domain). The `invoice/` module handles the full pipeline: PDF ingestion, hybrid classification, LLM-powered structured field extraction (electricity only in Phase 1), PII redaction, and persistence. The `market/` module is fully implemented: Kafka consumer (`ElectricityPriceConsumer`), `electricity_rates` persistence, `GET`/`DELETE /api/v1/admin/market-rates` (the rate corpus is admin-only in both directions, so it is served from inside the admin tree — the static viewer at `/market-rates.html` is a public shell that holds no data and asks for a token). The `knowledge/` module provides hybrid pgVector + BM25 retrieval with RRF over 5 seed regulatory documents. The `comparison/` module delivers deterministic savings calculations on every upload (`GET /api/v1/invoices/{id}/comparison`; comparison is also embedded in the `POST /api/v1/invoices` response). It ranks offers on **total annual cost** — energy plus the power term — through one cost function applied identically to the user's tariff and to every offer, so the tax factor can never be applied asymmetrically and an offer with cheap kWh but an expensive standing charge loses as it should. Taxes (IEE + IVA) scale a saving without reordering the offers, so they are applied once, at the end, to the difference. The result carries two savings of different epistemic weight: `periodSavingsEuros` is what the invoice in hand would have cost on the winning tariff — its own consumption and days, nothing extrapolated, so it keeps its cents and gets no band, and it is the one figure the user can check against the paper (the UI leads with it and the assistant is told to). The annual one is a **band** (`annualSavingsLow`/`High`, rounded outwards to tens) plus a `ComparisonBasis` stating what it rests on — days invoiced, whether the year was extrapolated, whether the power term was `READ`/`DERIVED`/`UNAVAILABLE`, whether the consumption profile is real or assumed. Every consumer prints the same caveats from that one payload: the API, the chat card (`static/chat/js/messages.js`) and the assistant's context. Before any of it, `ElectricityFields.reconcileWithTotal()` checks the extracted parts against the invoice's printed total and **measures** the tax ratio rather than assuming a rate — so a bill issued under a temporary reduced rate reconciles too, and a misread price (the failure that was previously invisible) yields no comparison at all instead of a confident wrong number. Because that engine is useless without a rate corpus, and the corpus only fills via Kafka, `MarketOfferQueryAdapter` serves six example 2.0TD offers (`comparison/fallback-electricity-offers.json`, three flat-price + three time-of-use so both comparison blocks get a winner and alternatives) **only while `electricity_rates` was never filled** — a read-path fallback, never persisted, so nothing needs cleaning up and real Kafka rates take over automatically. Rates expire on their producer-declared `validTo`: expired ones are excluded from every current-price reader (`findLatestPerTariff()` — comparison, chat market context, assistant tool) but kept by `findAll()`, which serves the admin listing as history. A corpus whose rates have all expired reports no alternatives rather than the examples, so a stale corpus stays visible instead of being papered over (`COMPARISON_FALLBACK_OFFERS_ENABLED=false` disables it; see `docs/MARKET.md`). The `assistant/` module provides conversational RAG with SSE streaming, in-memory multi-turn conversations (`conversationId` handshake), and dual context (the invoice's structured fields + regulatory retrieval). A static HTML chat UI at `/chat/` covers the full user flow. Milestone 6 is complete: the RAGAS-style eval harness is in place — a hybrid quality gate (`AssistantRagEvalIT`) with a deterministic embedding layer that always runs in CI (context precision, context recall, reference coverage over a 50-case Spanish golden set) plus an opt-in LLM-as-judge layer (faithfulness, answer relevancy, fact coverage) gated by `EVAL_LLM_ENABLED`; see `docs/EVAL.md`. Observability is fully live (`docs/OBSERVABILITY.md`): Micrometer/Actuator metrics plus per-call LLM telemetry emitted by `TimedChatLanguageModel` into composable `LlmTelemetry` sinks — `MetricsLlmTelemetry` (Actuator `llm.*` meters, on by default) and `TracingLlmTelemetry` (opt-in OpenTelemetry/OTLP spans exported to an external Langfuse backend via `LANGFUSE_HOST`, off by default). Security is hardened beyond the milestone track: admin access is split into authenticate-in-the-filter / authorize-in-the-engine layers (`JwtAuthFilter` populates the `SecurityContext`; `RouteAccessAuthorizationManager` decides; `@PreAuthorize` enforces a third time), and a per-endpoint token-bucket rate limiter (`_shared/infrastructure/ratelimit/`, bucket4j + Caffeine, pre/post-auth checkpoints, per-profile fail-open/closed) guards every `/api/v1/**` route — see `docs/RATELIMIT.md`. Milestone 7 is delivered: the schema is Flyway-managed (`db/migration/V1__baseline.sql`, `ddl-auto=validate`, adopted via `baseline-on-migrate`; see ARCHITECTURE Design Decision #13), PII is scrubbed from logs (`PiiScrubber` shared with invoice ingestion, wired as the `%pii` logback converter), and prompt-injection defenses are unified in `_shared/infrastructure/llm/prompt/` — `PromptFence` (per-request nonce markers) and `PromptText` (inline flattening), applied to invoice OCR, the assistant's context sections, every agentic tool result and the Kafka-sourced company/tariff names, with retrieved data moved out of the `system` role (ARCHITECTURE Design Decision #14). Beyond the milestone track, LLM provider failures are now classified at the single point every model call crosses (`LlmFailures` inside `TimedChatLanguageModel`): a throttle answers **429** with `Retry-After` and the same Spanish wait copy the rate limiter uses (`ThrottleMessages`), an outage answers **503**, and everything else — `InvalidRequestException` included — propagates untouched so the agentic loop keeps recovering from its own malformed tool calls. The chat's SSE stream reports these as `error` events carrying a `code`, since its status is committed when the stream opens. On the UI side `static/chat/js/errors.js` is the one place a failure becomes text (no path prints an HTTP status) and `cooldown.js` locks input and upload for exactly the wait, counting it down (ARCHITECTURE Design Decision #15, `docs/RATELIMIT.md`). See `docs/PLAN.md` for the full roadmap.

---

## Architecture

Hexagonal Architecture (Ports & Adapters) + DDD:

```
src/main/java/dev/izquierdo/billmind/
├── _shared/                          # Cross-cutting concerns
│   ├── application/
│   │   ├── command/                  # Command, CommandBus, CommandHandler
│   │   └── query/                    # Query, QueryBus, QueryHandler
│   ├── domain/
│   │   ├── event/                    # DomainEvent, BaseDomainEvent, DomainEventPublisher
│   │   │   └── handle/               # DomainEventHandler
│   │   ├── exceptions/               # ValidationErrorsException, LlmRateLimitedException (429 + retryAfter),
│   │   │                             # LlmServiceUnavailableException (503)
│   │   ├── model/                    # PaginatedResult<T>
│   │   │   └── fields/               # Supply-type field records: ElectricityFields, GasFields, WaterFields, TelecomFields (+ MobileLine, StreamingService)
│   │   └── port/                     # ExternalAuthPort (external identity delegation)
│   └── infrastructure/
│       ├── GlobalExceptionHandler    # Centralized @ControllerAdvice
│       ├── adapter/                  # ExternalAuthAdapter (GET /introspect, fail-closed)
│       ├── auth/                     # JwtAuthFilter (authenticates only), ExternalTokenAuthentication,
│       │                             # ApiSecurityErrorHandler (401/403), Roles, ManagementHealthAuthFilter
│       ├── command/                  # SimpleCommandBus
│       ├── config/                   # LangChain4jConfig (PgVectorEmbeddingStore), SecurityConfig, ManagementSecurityConfig, LlmTracingConfig, WebMvcConfig
│       │   └── embedding/            # Per-provider EmbeddingModel beans: AllMiniLm, OpenAi, Ollama
│       ├── dto/                      # ErrorResponseDTO, SuccessResponseDTO
│       ├── event/                    # SpringDomainEventPublisher (synchronous in-process bus)
│       ├── health/                   # StartupReadinessChecker, OllamaHealthIndicator, KafkaHealthIndicator
│       ├── kafka/                    # KafkaEvent, KafkaConsumerFactoryConfig
│       ├── llm/                      # TimedChatLanguageModel (implements ChatModel), LlmFailures (provider failure → 429/503, one point),
│       │                             # ModelPricingRegistry, LlmResponseJsonSanitizer, LlmTelemetry sinks (Metrics/Tracing)
│       │   └── prompt/               # PromptFence (nonce-delimited untrusted blocks), PromptText (inline value flattening)
│       ├── logging/                  # PiiRedactingMessageConverter (%pii — scrubs PII from rendered log messages via logback-spring.xml)
│       ├── persistence/              # SessionEntity, SessionJpaRepository
│       ├── pii/                      # PiiScrubber (pure regex PII redactor; shared by invoice ingestion + logging)
│       ├── query/                    # SimpleQueryBus
│       ├── ratelimit/                # Per-endpoint token-bucket limiter: RateLimitFilter, PostAuthRateLimitFilter,
│       │                             # RateLimiter, RateLimitStore (bucket4j + Caffeine), per-profile policies & keys,
│       │                             # ThrottleMessages (the Spanish "wait N minutes" copy, shared with the LLM 429)
│       ├── route/                    # RouteAccessPolicy, RouteAccess (ADMIN / ANONYMOUS / OPEN classifier),
│       │                             # RouteAccessAuthorizationManager (the access decision), RequestPathMatcher
│       └── session/                  # SessionContext, SessionFilter, SessionService
│
├── invoice/                          # Bounded Context: invoice ingestion & structured extraction
│   ├── domain/
│   │   ├── model/                    # Invoice, InvoiceClassification, SupplyDomain, InvoiceFields, ...
│   │   ├── port/                     # InvoiceParser, InvoiceClassifier, InvoiceFieldExtractor,
│   │   │                             # InvoiceRepository, PiiRedactor
│   │   └── exceptions/               # InvoiceNotFoundException, NotASupplyInvoiceException, ...
│   ├── application/
│   │   ├── command/                  # UploadInvoiceCommand, UploadInvoiceCommandHandler
│   │   ├── query/                    # GetInvoiceQuery, GetSessionInvoicesQuery, handlers
│   │   └── usecase/                  # UploadInvoiceUseCase, GetInvoiceUseCase, GetSessionInvoicesUseCase
│   └── infrastructure/
│       ├── adapter/
│       │   ├── classifier/           # KeywordInvoiceClassifier, LlmInvoiceClassifier
│       │   ├── fieldextractor/       # ExtractionPromptBuilder, InvoiceFieldsValidator
│       │   ├── pii/                  # HybridPiiRedactor (regex layer delegates to _shared PiiScrubber)
│       │   ├── HybridInvoiceClassifier    # implements InvoiceClassifier port
│       │   ├── LlmInvoiceFieldExtractor   # implements InvoiceFieldExtractor port
│       │   └── PdfInvoiceParser           # PDF bytes → plain text (implements InvoiceParser port)
│       ├── config/                   # chat/ (ChatModelRolesConfig, per-provider beans)
│       ├── controller/               # InvoiceController
│       │   └── dto/                  # InvoiceResponse, InvoiceUploadResponse
│       └── persistence/              # InvoiceEntity, JpaInvoiceRepository
│
├── assistant/                        # Bounded Context: conversational RAG (Milestone 5 — complete)
├── comparison/                       # Bounded Context: savings engine (Milestone 3 — complete)
├── knowledge/                        # Bounded Context: regulatory KB ingestion + hybrid search (Milestone 2 — complete)
├── market/                           # Bounded Context: market rate ingestion (Milestone 2 — Market Consumer, complete)
└── metrics/                          # Bounded Context: reacts to domain events (upload funnel + chat engagement) via DomainEventHandlers — handlers log-only until the metrics domain lands
```

### Rules (NEVER violate)

```
Infrastructure → Application → Domain
```

The `domain/` package must NOT import: Spring, JPA, LangChain4j, Lombok, Jackson. Only `java.*`.

---

## Code Conventions

**Language convention** (split — full table in `memory/feedback_language.md`):

| Artifact | Language |
|---|---|
| Source code, identifiers, code comments, Javadoc | English |
| Commit messages, PR descriptions | English |
| Project docs (`README.md`, `CLAUDE.md`, `docs/*.md`) | English |
| LLM **system prompts** (instructions, rules, output format) | English, ending with an explicit "respond in Spanish" instruction |
| Few-shot examples / invoice source text passed to the LLM as data | Spanish (domain data — never translated) |
| Assistant chat responses returned to the end user (generated by LLM) | Spanish |
| Hardcoded API error messages that reach the end user | Spanish |
| Internal logs, technical exception messages | English |

**Style:**
- Indentation: 4 spaces — Classes: `PascalCase` — Methods/variables: `camelCase` — Constants: `UPPER_SNAKE_CASE`
- Methods max 20 lines
- DTOs as Java 21 `record`
- Constructor injection only (never `@Autowired` on fields)

**Errors:**
- Custom domain exceptions (never raw `RuntimeException`)
- Logging only in the infrastructure layer
- `GlobalExceptionHandler` centralizes all error responses
- A `500` means we did not foresee the failure. An external dependency failing is foreseeable: classify it (throttle → `429` + `Retry-After`, outage → `503`) at the one point every call to it crosses, never per call site
- The provider's raw failure is the exception `cause` — for the logs. The response body is always built from `getMessage()`, a fixed Spanish string

**Git commits:**
```
feat|fix|refactor|test|security|docs(scope): brief description
```
Scopes: `invoice`, `assistant`, `comparison`, `market`, `shared`, `config`, `api`, `architecture`, `eval`, `security`

---

## LangChain4j 1.x Conventions

**Version management:** All LangChain4j modules are managed via `langchain4j-bom:1.0.0` in `<dependencyManagement>`. Do not add explicit versions to individual `dev.langchain4j` dependencies — the BOM resolves them (integrations like starters and pgvector ship as `1.0.0-beta5`; core modules like `langchain4j-open-ai` are at `1.0.0` final).

**HTTP client:** `langchain4j-open-ai:1.0.0` brings in `langchain4j-http-client-jdk` as a transitive dep. This conflicts with the `SpringRestClientBuilderFactory` from the Spring Boot starter. The JDK client is excluded from `langchain4j-open-ai` in `pom.xml`. Never re-add it.

**Core interface: `ChatModel`** (package `dev.langchain4j.model.chat`):
- `ChatLanguageModel` was the 0.x name — it no longer exists in 1.x.
- Implement `doChat(ChatRequest request)` to create a custom model (e.g. `TimedChatLanguageModel`).
- Call `model.chat(String prompt)` → returns `String` directly (convenience method).
- Call `model.chat(List<ChatMessage>)` → returns `ChatResponse`.
- `ChatRequest` lives in `dev.langchain4j.model.chat.request`, `ChatResponse` in `dev.langchain4j.model.chat.response`.
- `TokenUsage` is still in `dev.langchain4j.model.output.TokenUsage`; access via `chatResponse.tokenUsage()`.
- `generate()` is removed — always use `chat()`.

**Role beans pattern** (`ChatModelRolesConfig`):
- `fastChatModel` — low-latency tasks (classification, PII).
- `smartChatModel` — quality-sensitive tasks (field extraction, RAG).
- Both are `TimedChatLanguageModel` wrappers injected with `@Qualifier`. Consumers ask for a **role**, never a provider — which model serves a role is decided in one place.
- Each per-provider config publishes a `ChatModelFactory` (`ChatModel create(String modelName, int maxOutputTokens)`), not a finished `ChatModel`. `ChatModelRolesConfig` calls it once per role, so the two roles can run different models on the same provider. Never bind a model name inside a provider config — it would collapse the roles back into one.
- `llm.role.fast.model` / `llm.role.smart.model` are declared in **both** `application.properties` files (main and test), each defaulting to `${llm.${llm.provider}.model}`. The fallback is that nested default, not Java: the config reads the two keys with a plain `@Value`, like it reads `llm.provider`. A key declared in only one of the two files fails every `@SpringBootTest` at placeholder resolution.
- The model name is resolved **per role** and handed to `TimedChatLanguageModel` as its telemetry tag; resolving it once for both would misattribute cost.
- **The output cap belongs to the model, never to the request — and to the role, never to the environment.** `langchain4j-anthropic` resolves to `1.0.0-beta5`, a pre-migration integration whose `doChat()` rejects *any* per-request parameter — a `ChatRequestParameters.maxOutputTokens(...)` throws `UnsupportedFeatureException`. The ceiling is a constant per role in `ChatModelRolesConfig` (fast 512, smart 2048) and each provider maps it onto its builder (`maxTokens`, or `numPredict` on Ollama). It is not a property: what sets it is the longest answer that role asks for, which does not vary between environments. Keep it tight — OpenAI-compatible providers charge the *declared* cap against the per-minute token budget on every call regardless of the answer, so an oversized fast cap buys a 429 on every upload.

**`TimedChatLanguageModel` wrapping pattern:** When wrapping a `ChatModel` for instrumentation, override `chat(ChatRequest)` (not `doChat(ChatRequest)`, not `chat(List<ChatMessage>)`) and call `delegate.chat(request)`. The call chain from callers is: `chat(String)` → default `ChatModel.chat(ChatRequest)` → our override → `delegate.chat(request)`. Provider models override `chat(ChatRequest)` internally to convert `DefaultChatRequestParameters` → provider-specific params (e.g. `OpenAiChatRequestParameters`) before calling `doChat()`. Bypassing that step by calling `delegate.doChat(request)` directly causes a `ClassCastException`. `chat(List<ChatMessage>)` is NOT reached from `chat(String)` — overriding it is dead code. `doChat()` must still be implemented as a passthrough since the interface declares it abstract.

**`PgVectorEmbeddingStore` (IVFFlat):** `langchain4j-pgvector:1.0.0-beta5` requires both `.useIndex(boolean)` **and** `.indexListSize(int)` — omitting `indexListSize` throws `indexListSize must be greater than zero, but is: null` at startup even when `useIndex=false`. Always supply both. `LangChain4jConfig` hardcodes `.useIndex(false).indexListSize(1)`: the store never builds an index itself — the index lifecycle is owned by `JpaKnowledgeRepository.rebuildIndex()`, which drops/recreates the IVFFlat index with `lists = sqrt(rows)` (skipped below 100 vectors). These two builder args are **not** wired to any property; a `@Value`-injected value would only be read by the store's own index path, which is disabled. See `ARCHITECTURE.md` → Design Decision #3.

**Testing mocks:** Mock `ChatModel` with Mockito. Stub `delegate.doChat(any(ChatRequest.class))`. Build responses with `ChatResponse.builder().aiMessage(AiMessage.from("...")).build()`. `ChatRequest` requires at least one message — use `ChatRequest.builder().messages(UserMessage.from("test")).build()`.

---

## Tests

| Layer | Type | Annotations |
|---|---|---|
| `domain/` | Pure unit test | `@Test` only |
| `application/usecase/` | Unit test with Mockito | `@ExtendWith(MockitoExtension.class)` |
| `infrastructure/adapter/` | Unit test with Mockito | `@ExtendWith(MockitoExtension.class)` |
| `infrastructure/adapter/` (`*IT.java`) | Integration test | `@SpringBootTest` + TestContainers |
| `infrastructure/controller/` | MVC slice test | `@WebMvcTest` + `@MockitoBean` |
- Naming: `should[State]When[Condition]()` or `given[Ctx]_when[Action]_then[Result]()`
- Integration tests suffixed `*IT.java`
- Never mock the class under test
- Always cover happy path + null cases + edge cases

---

## Security

1. Never hardcode credentials — always `@Value("${property}")`
2. Validate uploaded PDFs: real MIME type, max size, sanitized filename
3. Never concatenate user input into SQL queries — use JPA / prepared parameters
4. Never concatenate untrusted text into an LLM prompt. Wrap it with `_shared/infrastructure/llm/prompt/`: `PromptFence.random().wrap(...)` for blocks (per-request nonce markers — never invent a fixed delimiter), `PromptText.inline(...)` for short values interpolated into a line. Retrieved data belongs in the `user` role, never `system`, and the data block is followed by a trailing instruction block (the sandwich). "Untrusted" includes anything from the KB, the Kafka producer, and the model's own tool arguments echoed back
5. CORS must never use `*` in production with authenticated endpoints
6. Logs must not contain invoice content, JWT tokens, or credentials
7. PII (IBAN, DNI, postal address, full name, phone) must be redacted before persisting `Invoice` rows — the aggregated corpus is treated as a public-by-design dataset
8. Access decisions belong to the authorization engine, never to a filter's `shouldNotFilter()`. Filters authenticate (populate the `SecurityContext`); `authorizeHttpRequests` decides; admin handlers carry `@PreAuthorize("hasRole('ADMIN')")` as an independent third layer. Never write `anyRequest().permitAll()` — it idles the engine and leaves path matching as the only guard
9. Never derive an identity (rate-limit key, audit subject, ownership check) from a raw request header. Read it from the `SecurityContext`, where it exists only if it was validated

---

## API Response Format

```json
{ "success": true,  "status": 200, "message": "...", "data": { "..." } }
{ "success": false, "status": 400, "message": "...", "errors": { "field": { "code": "message" } } }
```

`data` is omitted when `null`. `errors` is omitted when there are no field-level details.

User-facing `message` strings are in Spanish.

---

## Available Agents (`.ai/agents/`)

| Agent | File | When to invoke |
|---|---|---|
| Architect | `architect.md` | Designing new features or DDD modules |
| Developer | `developer.md` | Implementing Use Cases, Adapters, Controllers |
| Domain Expert | `domain-expert.md` | Validating naming, ubiquitous language, business rules |
| Tester | `tester.md` | Writing tests or validating coverage |
| Security | `security.md` | Auditing new code, endpoints, file handling |

**Flow for new features:** Architect → Domain Expert → Developer → Tester → Security
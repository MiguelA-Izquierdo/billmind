# CLAUDE.md — BillMind

> Developer tooling configuration for [Claude Code](https://docs.anthropic.com/en/docs/claude-code). Analogous to `.editorconfig` for LLM-assisted development — not part of the application runtime. For architecture decisions and extension points, see `@docs/ARCHITECTURE.md`. For the long-term roadmap, see `@docs/PLAN.md`.

---

## Project Description

**BillMind** is a Spring Boot 3.5.0 + Java 21 + LangChain4j 1.0.0 REST API. It ingests utility invoice PDFs, extracts structured fields, and stores them in PostgreSQL 16. A regulatory knowledge base (CNMC, REE, BOE documents) is stored in pgVector (IVFFlat, 384 dim — HNSW not yet in `langchain4j-pgvector:1.0.0-beta5`) with AllMiniLM-L6-v2 embeddings (local ONNX) and used for RAG in the chat assistant.

The product lets an anonymous visitor upload an invoice and (a) compare it against current market data and (b) chat about it. The chat uses dual context: the user's full invoice text passed directly to the LLM + semantic retrieval from the regulatory knowledge base. User accounts are out of scope for Phase 1 — the frontend generates a UUID per session and sends it as `X-Session-Id`. Auth lands in Milestone 9 (Phase 2).

**Current state:** Milestones 0–6 are complete; Milestone 7 (production hardening) is in progress. The `invoice/` module handles the full pipeline: PDF ingestion, hybrid classification, LLM-powered structured field extraction (electricity only in Phase 1), PII redaction, and persistence. The `market/` module is fully implemented: Kafka consumer (`ElectricityPriceConsumer`), `electricity_rates` persistence, `GET /api/v1/market-rates` endpoint. The `knowledge/` module provides hybrid pgVector + BM25 retrieval with RRF over 5 seed regulatory documents. The `comparison/` module delivers deterministic savings calculations on every upload (`GET /api/v1/invoices/{id}/comparison`; comparison is also embedded in the `POST /api/v1/invoices` response). The `assistant/` module provides conversational RAG with SSE streaming, in-memory multi-turn conversations (`conversationId` handshake), and dual context (invoice text + regulatory retrieval). A static HTML chat UI at `/chat/` covers the full user flow. Milestone 6 is complete: the RAGAS-style eval harness is in place — a hybrid quality gate (`AssistantRagEvalIT`) with a deterministic embedding layer that always runs in CI (context precision, context recall, reference coverage over a 50-case Spanish golden set) plus an opt-in LLM-as-judge layer (faithfulness, answer relevancy, fact coverage) gated by `EVAL_LLM_ENABLED`; see `docs/EVAL.md`. Observability is fully live (`docs/OBSERVABILITY.md`): Micrometer/Actuator metrics plus per-call LLM telemetry emitted by `TimedChatLanguageModel` into composable `LlmTelemetry` sinks — `MetricsLlmTelemetry` (Actuator `llm.*` meters, on by default) and `TracingLlmTelemetry` (opt-in OpenTelemetry/OTLP spans exported to an external Langfuse backend via `LANGFUSE_HOST`, off by default). Security is hardened beyond the milestone track: admin access is split into authenticate-in-the-filter / authorize-in-the-engine layers (`JwtAuthFilter` populates the `SecurityContext`; `RouteAccessAuthorizationManager` decides; `@PreAuthorize` enforces a third time), and a per-endpoint token-bucket rate limiter (`_shared/infrastructure/ratelimit/`, bucket4j + Caffeine, pre/post-auth checkpoints, per-profile fail-open/closed) guards every `/api/v1/**` route — see `docs/RATELIMIT.md`. Milestone 7 is partially delivered: the schema is now Flyway-managed (`db/migration/V1__baseline.sql`, `ddl-auto=validate`, adopted via `baseline-on-migrate`; see ARCHITECTURE Design Decision #13) and PII is scrubbed from logs (`PiiScrubber` shared with invoice ingestion, wired as the `%pii` logback converter). The remaining M7 item — advanced prompt-injection defenses beyond the existing extraction-prompt sandwich — is still open. See `docs/PLAN.md` for the full roadmap.

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
│   │   ├── exceptions/               # ValidationErrorsException
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
│       ├── llm/                      # TimedChatLanguageModel (implements ChatModel), ModelPricingRegistry, LlmResponseJsonSanitizer, LlmTelemetry sinks (Metrics/Tracing)
│       ├── logging/                  # PiiRedactingMessageConverter (%pii — scrubs PII from rendered log messages via logback-spring.xml)
│       ├── persistence/              # SessionEntity, SessionJpaRepository
│       ├── pii/                      # PiiScrubber (pure regex PII redactor; shared by invoice ingestion + logging)
│       ├── query/                    # SimpleQueryBus
│       ├── ratelimit/                # Per-endpoint token-bucket limiter: RateLimitFilter, PostAuthRateLimitFilter,
│       │                             # RateLimiter, RateLimitStore (bucket4j + Caffeine), per-profile policies & keys
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
- Both are `TimedChatLanguageModel` wrappers injected with `@Qualifier`.
- The underlying provider bean is named `chatLanguageModel` — keep that name so Spring resolves it by parameter name when multiple `ChatModel` beans exist in context.

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
4. Never concatenate user input into LLM system prompts (prompt injection). Use the sandwich pattern: instructions → delimited data → instructions
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

## Available Agents (`.agents/`)

| Agent | File | When to invoke |
|---|---|---|
| Architect | `architect.md` | Designing new features or DDD modules |
| Developer | `developer.md` | Implementing Use Cases, Adapters, Controllers |
| Domain Expert | `domain-expert.md` | Validating naming, ubiquitous language, business rules |
| Tester | `tester.md` | Writing tests or validating coverage |
| Security | `security.md` | Auditing new code, endpoints, file handling |

**Flow for new features:** Architect → Domain Expert → Developer → Tester → Security
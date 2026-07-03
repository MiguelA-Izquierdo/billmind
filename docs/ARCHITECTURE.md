# Architecture & Design Decisions — BillMind

Reference for sessions where modules are designed or extended. Use with `@CLAUDE.md` for conventions and agent instructions. For the milestone roadmap, see `@docs/PLAN.md`.

---

## Design Decisions

1. **UUID generated in the controller** — strict CQRS: commands return no value (`CommandBus.dispatch()` returns `void`).
2. **Pluggable embedding model** — selected at runtime via `EMBEDDING_PROVIDER` (`allminilm` default). `allminilm` runs as local ONNX inside the JVM (384d, no network call). `ollama` delegates to a local Ollama server (e.g. `bge-m3` at 1024d for better Spanish). `openai` uses the Embeddings API. Changing the model requires dropping `vector_store` and re-ingesting — the pgVector column dimension is a schema constraint.
3. **Automatic IVFFlat index management** — `PgVectorEmbeddingStore` is always configured with `useIndex=false`; the app manages the index lifecycle itself via `JpaKnowledgeRepository.rebuildIndex()`. At startup (after seed) and on `POST /admin/knowledge/reindex`: if vectors < 100 → no index (sequential scan); if vectors ≥ 100 → `DROP + CREATE INDEX` with `lists = sqrt(rows)`. This keeps `lists` correct as the dataset grows without any manual env var tuning. HNSW is the target for a future upgrade when available in `langchain4j-pgvector`.
4. **Chunk size 150, overlap 30** — tuned to AllMiniLM-L6-v2's 256-token limit. Configurable via `KNOWLEDGE_CHUNK_SIZE` and `KNOWLEDGE_CHUNK_OVERLAP`.
5. **Pluggable LLM provider** — selected at runtime via `LLM_PROVIDER` env var (`ollama` default). Ollama keeps all data local; cloud providers (OpenAI, Anthropic, Gemini, Groq) are available for environments where external API calls are acceptable. See *LLM Provider Strategy* section below.
6. **Domain Events** wired to Spring's `ApplicationEventPublisher`. The first active listener is introduced in Milestone 5, where `InvoiceProcessed` triggers the comparison pipeline.
7. **Frontend-generated session UUID** — sent as `X-Session-Id` header. The backend correlates resources to it but does not authenticate (Phase 1 is anonymous). Auth lands in Milestone 7.
10. **Admin route protection via external auth microservice** — destructive admin operations (e.g. `DELETE /api/v1/market-rates`) are guarded by `JwtAuthFilter` before `SessionFilter` runs. The filter delegates token validation to an external user service via `ExternalAuthPort` / `ExternalAuthAdapter`: the adapter calls `GET <AUTH_EXTERNAL_URL>/introspect` forwarding the `Authorization: Bearer <token>` header as-is; a 200 response means the token is valid and the request proceeds; 401/403 and any I/O error fail closed (the adapter returns `false`). The boundary between BillMind and the auth service is the port — swapping the external service requires only a new adapter, not changes to the filter or domain. Admin routes are still declared as "public" in `PublicRoutesService` so `SessionFilter` skips them; `JwtAuthFilter` is the sole gatekeeper for those routes. Adding more admin routes requires only editing `AdminRoutesService.ADMIN_ROUTES`.
8. **PII redaction before persistence** — the aggregated invoice corpus is a product moat and must be safe by design. IBAN, DNI, postal address, full name, and phone are replaced with placeholders before storing.
9. **English system prompts with "respond in Spanish" instruction** — small Ollama models follow English instructions more reliably; output language is controlled by an explicit instruction at the end of the prompt.

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

BillMind **never validates tokens itself**. All authentication is delegated to an external user microservice via `ExternalAuthPort`. This applies to admin routes today and will extend to all authenticated endpoints in Milestone 7.

The filter chain for an incoming request:

```
JwtAuthFilter          ← only activates for routes in AdminRoutesService.ADMIN_ROUTES
  └─ ExternalAuthAdapter.isAuthorized(bearerToken)
       └─ GET <AUTH_EXTERNAL_URL>/introspect   (Authorization: Bearer <token>)
            200 → true   →  chain continues
            4xx / error  →  false → 401 or 403 returned immediately
SessionFilter          ← skips admin routes (they stay in PublicRoutesService)
  └─ requires X-Session-Id for all other /api/v1/ routes
Controller
```

Key files:

| File | Role |
|---|---|
| `_shared/domain/port/ExternalAuthPort` | Port — `isAuthorized(String bearerToken): boolean` |
| `_shared/infrastructure/adapter/ExternalAuthAdapter` | Adapter — `GET /introspect`, fail-closed on any error |
| `_shared/infrastructure/auth/JwtAuthFilter` | `OncePerRequestFilter` — extracts Bearer, calls port, rejects with 401/403 |
| `_shared/infrastructure/auth/AdminRoutesService` | Declares which method+path pairs require JWT |
| `_shared/infrastructure/config/SecurityConfig` | Registers `JwtAuthFilter` before `SessionFilter` |

Config: `AUTH_EXTERNAL_URL` env var → `app.auth.external-url`.

## Adding user authentication (Milestone 7)

Authentication remains delegated to the external microservice — no local token validation is added to BillMind. The work for Milestone 7 is:

- Extend `JwtAuthFilter` (or introduce a parallel filter) to cover user-facing endpoints, not just admin routes.
- `ExternalAuthAdapter.isAuthorized` can be enriched to return the resolved subject/roles from the `/introspect` response if downstream logic needs them (e.g. scoping invoice queries to the authenticated user instead of the session UUID).
- Migration: add nullable `user_id` to `sessions`; on login, link the current anonymous session to the new user. Anonymous historical invoices remain part of the dataset.
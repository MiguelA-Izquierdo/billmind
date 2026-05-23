# Architecture & Design Decisions — BillMind

Reference for sessions where modules are designed or extended. Use with `@CLAUDE.md` for conventions and agent instructions. For the milestone roadmap, see `@docs/PLAN.md`.

---

## Design Decisions

1. **UUID generated in the controller** — strict CQRS: commands return no value (`CommandBus.dispatch()` returns `void`).
2. **AllMiniLM-L6-v2** via local ONNX runtime (bundled in the jar) — no external API, 384 dimensions, fast. Ollama is not involved in embeddings.
3. **IVFFlat** as the pgVector index — only index type supported by `langchain4j-pgvector:1.0.0-beta5`. HNSW is the target for a future upgrade when available in the LangChain4j release.
4. **Chunk size 500, overlap 100** — configurable if better parameters emerge.
5. **Pluggable LLM provider** — selected at runtime via `LLM_PROVIDER` env var (`ollama` default). Ollama keeps all data local; cloud providers (OpenAI, Anthropic, Gemini, Groq) are available for environments where external API calls are acceptable. The embedding model (AllMiniLM-L6-v2) is always local ONNX regardless of provider. See *LLM Provider Strategy* section below.
6. **Domain Events** wired to Spring's `ApplicationEventPublisher`. The first active listener is introduced in Milestone 5, where `InvoiceProcessed` triggers the comparison pipeline.
7. **Frontend-generated session UUID** — sent as `X-Session-Id` header. The backend correlates resources to it but does not authenticate (Phase 1 is anonymous). Auth lands in Milestone 7.
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

**The embedding model is always `AllMiniLmL6V2EmbeddingModel` (local ONNX, 384 dim) and is never switchable.** Changing it would require dropping and recreating the `vector_store` table and re-ingesting all documents — the pgVector dimension is a schema constraint, not a runtime setting.

**Security note:** when using a cloud provider, invoice text is sent to a third-party API. PII redaction (rule #7 in CLAUDE.md) must have run before any LLM call.

**Adding a new provider:**
1. If the provider has a native LangChain4j integration, add the `langchain4j-<provider>` dependency to `pom.xml`. If it exposes an OpenAI-compatible API, skip this step — reuse `langchain4j-openai` via `OpenAiChatModel.builder().baseUrl(...)`.
2. Add a `@Configuration @ConditionalOnProperty(name = "llm.provider", havingValue = "<provider>")` class under `invoice/infrastructure/config/chat/`.
3. Add the `llm.<provider>.*` properties to `application.properties`.
4. Add the API key check to `StartupReadinessChecker`.
5. Update this table.

---

## Adding JWT authentication (Milestone 7)

- Spring Security filter in `_shared/infrastructure/security/`.
- `JWT_SECRET` and `JWT_EXPIRATION` are read from `application.properties` via `@Value`.
- A `SecurityFilterChain` bean configures protected endpoints.
- Migration: add nullable `user_id` to `sessions`; on login, link the current anonymous session to the new user. Anonymous historical invoices remain part of the dataset.
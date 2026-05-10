# Architecture & Design Decisions — BillMind

Reference for sessions where modules are designed or extended. Use with `@CLAUDE.md` for conventions and agent instructions. For the milestone roadmap, see `@docs/PLAN.md`.

---

## Design Decisions

1. **UUID generated in the controller** — strict CQRS: commands return no value (`CommandBus.dispatch()` returns `void`).
2. **AllMiniLM-L6-v2** local via Ollama — no external API, 384 dimensions, fast.
3. **HNSW** as the pgVector index — good speed/precision trade-off.
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

## Adding semantic search (RAG)

- Port in `invoice/domain/port/InvoiceSearchRepository.java`.
- Implementation in `invoice/infrastructure/adapter/PgVectorSearchRepository.java`.
- Use `EmbeddingStoreRetriever` from LangChain4j, plus Postgres `tsvector` BM25 + Reciprocal Rank Fusion (Milestone 2).
- LLM prompts live in `infrastructure/ai/prompts/` (never in the domain layer).

## LLM Provider Strategy

The `ChatLanguageModel` bean is selected at startup via `LLM_PROVIDER`. All providers implement the same LangChain4j `ChatLanguageModel` interface — no application code changes when switching.

| `LLM_PROVIDER` | Provider | Required env vars | Default model |
|---|---|---|---|
| `ollama` *(default)* | Local Ollama | `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL` | `llama3.2` |
| `openai` | OpenAI | `OPENAI_API_KEY` | `gpt-4o` |
| `anthropic` | Anthropic | `ANTHROPIC_API_KEY` | `claude-sonnet-4-6` |
| `gemini` | Google AI Studio | `GEMINI_API_KEY` | `gemini-1.5-pro` |
| `groq` | Groq (OpenAI-compatible) | `GROQ_API_KEY` | `llama-3.3-70b-versatile` |

**The embedding model is always `AllMiniLmL6V2EmbeddingModel` (local ONNX, 384 dim) and is never switchable.** Changing it would require dropping and recreating the `vector_store` table and re-ingesting all documents — the pgVector dimension is a schema constraint, not a runtime setting.

**Security note:** when using a cloud provider, invoice text is sent to a third-party API. PII redaction (rule #7 in CLAUDE.md) must have run before any LLM call.

**Adding a new provider:**
1. Add the `langchain4j-<provider>` dependency to `pom.xml`.
2. Add a `@Bean @ConditionalOnProperty(name = "llm.provider", havingValue = "<provider>")` method in `ChatModelConfig`.
3. Add the `llm.<provider>.*` properties to `application.properties`.
4. Update this table.

---

## Adding JWT authentication (Milestone 7)

- Spring Security filter in `_shared/infrastructure/security/`.
- `JWT_SECRET` and `JWT_EXPIRATION` are read from `application.properties` via `@Value`.
- A `SecurityFilterChain` bean configures protected endpoints.
- Migration: add nullable `user_id` to `sessions`; on login, link the current anonymous session to the new user. Anonymous historical invoices remain part of the dataset.
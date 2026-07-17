# BillMind — Configuration Reference

All configuration is driven by environment variables. Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

---

## Server

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port the API listens on |
| `MANAGEMENT_PORT` | `8083` | Internal-only port for Actuator (health, metrics, prometheus). Kept separate from the application port and **not** published by Docker — point liveness/readiness probes here over the internal network. |

---

## Database

| Variable | Example | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/billmind` | PostgreSQL JDBC URL |
| `DB_HOST` | `localhost` | Used by Docker Compose health checks |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `billmind` | Database name |
| `DB_USERNAME` | `billmind` | Database user |
| `DB_PASSWORD` | `billmind` | Database password |
| `DDL_AUTO` | `validate` | Hibernate schema mode (`spring.jpa.hibernate.ddl-auto`). The schema is Flyway-managed, so Hibernate only validates the mappings against it. Escape hatch only (e.g. `none`); **never set `update`/`create` on a Flyway-managed DB** — the two would fight over the schema. |
| `FLYWAY_ENABLED` | `true` | Runs Flyway migrations from `db/migration/` at startup (`spring.flyway.enabled`). Adopts an existing schema via `baseline-on-migrate=true` + `baseline-version=0`. Disabled in the test profile. |

> **Required extensions:** the schema depends on the `vector` (pgVector) and `unaccent` extensions. `V1__baseline.sql` runs `CREATE EXTENSION IF NOT EXISTS` for both, which needs a role with `CREATE` privilege on the database (a superuser locally; the `rds_superuser`/`cloudsqlsuperuser` role on managed Postgres). On a managed instance where the app's migration user is **not** privileged, pre-provision the extensions out of band (e.g. AWS RDS `CREATE EXTENSION` as the master user, or the console's shared-preload/extensions allowlist) before the first migration — the `IF NOT EXISTS` then makes V1 a no-op for that statement. The local Docker image (`pgvector/pgvector:pg16`) and `docker/postgres/init.sql` already handle this.

---

## LLM Provider

Set `LLM_PROVIDER` to activate one backend. Only the matching credential block is required.

| Variable | Options | Description |
|---|---|---|
| `LLM_PROVIDER` | `ollama` \| `openai` \| `anthropic` \| `gemini` \| `groq` | Active LLM backend |

### Ollama (local — default)

No API key required. Requires Ollama running locally or via `docker-compose --profile local-ai`.

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_CHAT_MODEL` | `llama3.2` | Chat/classification model |
| `OLLAMA_TIMEOUT_SECONDS` | `240` | Request timeout in seconds (increase for slow hardware) |

Pull the chat model before starting (if running Ollama outside Docker):

```bash
ollama pull llama3.2     # chat — required when LLM_PROVIDER=ollama
```

### OpenAI

| Variable | Example | Description |
|---|---|---|
| `OPENAI_API_KEY` | `sk-...` | OpenAI API key |
| `OPENAI_MODEL` | `gpt-4o` | Model name |

### Anthropic

| Variable | Example | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | `sk-ant-...` | Anthropic API key |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-6` | Model name |

### Google Gemini

| Variable | Example | Description |
|---|---|---|
| `GEMINI_API_KEY` | `AIza...` | Google AI API key |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Model name |

### Groq

| Variable | Default / Example | Description |
|---|---|---|
| `GROQ_API_KEY` | `gsk_...` | Groq API key |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Model name |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Groq OpenAI-compatible endpoint |

---

## Embedding Model

Set `EMBEDDING_PROVIDER` to choose the vector embedding backend. The selected model determines the vector dimensions stored in pgVector — **changing the model requires clearing `vector_store` and re-ingesting all documents**.

| Variable | Options | Default | Description |
|---|---|---|---|
| `EMBEDDING_PROVIDER` | `allminilm` \| `openai` \| `ollama` | `allminilm` | Active embedding backend |

### allminilm (local — default)

Runs entirely inside the JVM via ONNX. No API key, no network call, no Ollama instance needed. Fixed 384-dimensional output.

| Variable | Default | Description |
|---|---|---|
| — | — | No configuration required |

### openai

Uses the OpenAI Embeddings API. Requires `OPENAI_API_KEY` (shared with `LLM_PROVIDER=openai`).

| Variable | Default | Description |
|---|---|---|
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | OpenAI embedding model name |

Known dimensions:

| Model | Dimensions |
|---|---|
| `text-embedding-3-small` | 1536 |
| `text-embedding-3-large` | 3072 |
| `text-embedding-ada-002` | 1536 |

### ollama

Uses a locally running Ollama server to generate embeddings. The model must be pulled before starting.

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Ollama embedding model name |
| `OLLAMA_EMBEDDING_TIMEOUT_SECONDS` | `120` | Embedding request timeout in seconds (increase if the knowledge-base seed times out on large documents). |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Shared with `LLM_PROVIDER=ollama` |

Pull the embedding model before starting:

```bash
ollama pull nomic-embed-text   # 768d
ollama pull mxbai-embed-large  # 1024d
```

> **Startup validation:** BillMind always embeds a test string at startup and compares the vector length against `PGVECTOR_DIMENSIONS`. A mismatch causes an immediate startup failure with a clear error message.

---

## Knowledge Base

| Variable | Default | Description |
|---|---|---|
| `KNOWLEDGE_SEED_ENABLED` | `true` | Auto-load the 5 seed regulatory documents at startup and rebuild the IVFFlat index. Set to `false` to skip seeding (e.g. when the corpus is already populated). |
| `KNOWLEDGE_CHUNK_SIZE` | `150` | Chunk size in words for the ingestion sliding window. Tuned to AllMiniLM-L6-v2's 256-token limit. |
| `KNOWLEDGE_CHUNK_OVERLAP` | `30` | Word overlap between consecutive chunks, to avoid splitting context across chunk boundaries. |
| `KNOWLEDGE_SEARCH_MIN_VECTOR_SCORE` | `0.72` | Minimum cosine similarity (0–1) a vector candidate must have to enter the RRF fusion step. Filters out semantically weak matches before ranking. Lower values increase recall at the cost of precision. Tune per embedding model: AllMiniLM-L6-v2 typically scores lower than OpenAI or nomic-embed-text on Spanish content. |
| `KNOWLEDGE_SEARCH_MAX_RESULTS` | `5` | Maximum number of results returned per search query. |

> **Tuning tip:** The `0.72` default suits high-quality multilingual embedders (`bge-m3`, OpenAI). Drop to `0.50` for AllMiniLM-L6-v2, which scores lower on Spanish (the eval ITs use `0.3` for this reason), and to `~0.60` for `nomic-embed-text`. Raise it to enforce higher precision in production.

---

## Assistant

| Variable | Default | Description |
|---|---|---|
| `ASSISTANT_TOOLS_ENABLED` | `false` | `true` → agentic adapter: the LLM pulls comparison / market rates / regulation on demand via tool calling. `false` → eager adapter: all context is loaded up front into the prompt. **Requires a tool-capable `smartChatModel`** when enabled (cloud models or Groq's `llama-3.3-70b-versatile`; small local Ollama models are unreliable). See [`ASSISTANT.md`](ASSISTANT.md). |
| `ASSISTANT_CONVERSATION_MAX_SIZE` | `1000` | Hard cap on stored in-memory conversations; least-recently-accessed are evicted first. |
| `ASSISTANT_CONVERSATION_TTL` | `PT2H` | Sliding TTL (ISO-8601 duration) refreshed on each read/write; idle conversations expire. |

---

## Vector Store (pgVector)

| Variable | Default | Description |
|---|---|---|
| `PGVECTOR_TABLE_NAME` | `vector_store` | Table name for vector storage |
| `PGVECTOR_DIMENSIONS` | `384` | Embedding dimensions — must match the model (`allminilm` → 384, `bge-m3` → 1024) |

**Index management** is automatic — no manual configuration required:

- On every startup and after every `POST /api/v1/admin/knowledge/reindex`, the app counts the vectors in `vector_store` and decides:
  - **< 100 vectors** → no index, sequential scan (fast enough at this scale)
  - **≥ 100 vectors** → IVFFlat index with `lists = sqrt(rows)`, recalculated each time

This means the index always has the right number of lists for the current dataset size. HNSW is the target for a future upgrade when supported by `langchain4j-pgvector`.

---

## Kafka

| Variable | Default | Description |
|---|---|---|
| `KAFKA_ENABLED` | `false` | Set to `true` to activate the market price consumer and the startup Kafka reachability check. Required when using `--profile kafka`. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address. Use `kafka:29092` for the Docker-internal listener when running via Docker Compose. |
| `KAFKA_TOPIC_PARTITIONS` | `3` | Number of partitions for auto-created topics. |
| `KAFKA_TOPIC_REPLICAS` | `1` | Replication factor for auto-created topics. Set to `≥2` in production clusters. |

### Authenticated brokers (Confluent Cloud, MSK, etc.)

Uncomment and fill in your `.env` when connecting to a broker that requires SASL authentication:

| Variable | Example | Description |
|---|---|---|
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | Security protocol (`PLAINTEXT` by default for local dev). |
| `KAFKA_SASL_MECHANISM` | `PLAIN` | SASL mechanism (`PLAIN`, `SCRAM-SHA-256`, etc.). |
| `KAFKA_SASL_JAAS_CONFIG` | `org.apache.kafka.common.security.plain.PlainLoginModule required username="u" password="p";` | Full JAAS config string. |

---

## CORS

| Variable | Example | Description |
|---|---|---|
| `CORS_ALLOWED_ORIGIN` | `http://localhost:3000` | Comma-separated list of allowed origins. Never use `*` in production with authenticated endpoints. |

---

## External authentication

Authentication is delegated to an external microservice — BillMind does not sign or validate tokens locally. `JwtAuthFilter` forwards the `Authorization: Bearer …` header to the service's introspection endpoint via `ExternalAuthPort`. In Phase 1 only admin routes are guarded; user-facing endpoints are opened up in Milestone 9.

| Variable | Example | Description |
|---|---|---|
| `AUTH_EXTERNAL_URL` | `http://localhost:8081` | Base URL of the auth microservice exposing `GET /introspect` |

---

## Rate limiting

A per-endpoint token-bucket limiter guards every `/api/v1/**` route. Full design (profiles, IP ceiling, fail policy, store migration) → [`RATELIMIT.md`](RATELIMIT.md).

### Global

| Variable | Default | Description |
|---|---|---|
| `RATELIMIT_STORE` | `caffeine` | Bucket backend. `caffeine` = in-process, per-instance (Phase 1). `redis` (Lettuce) shares buckets across instances behind the same `RateLimitStore` port (Milestone 9). |
| `RATELIMIT_TRUST_FORWARDED_FOR` | `false` | Honour `X-Forwarded-For` for the client IP. Enable **only** behind a trusted proxy that overwrites it; otherwise the header is attacker-spoofable and the IP ceiling can be bypassed. |
| `RATELIMIT_CAFFEINE_MAX_SIZE` | `50000` | Max distinct keys held in the Caffeine store. |
| `RATELIMIT_CAFFEINE_EXPIRE_AFTER` | `PT1H` | How long an idle bucket survives (ISO-8601 duration). |

### Per-profile buckets

Each route resolves to a profile (`UPLOAD`, `CHAT`, `ADMIN`, `PUBLIC_READ`, `DEFAULT`; `NONE` is unlimited). Every profile has a `capacity`, a `refill-tokens` per `refill-period`, and a per-request `cost`, all overridable via env vars — starting points live in `application.properties` and should be tuned from metrics once live. `UPLOAD`, `CHAT` and `DEFAULT` additionally declare an **IP ceiling** (`overrides.ip.*`) because `X-Session-Id` is self-asserted and a session-only bucket is bypassed by rotating the header.

| Variable (pattern) | Example default | Description |
|---|---|---|
| `RATELIMIT_UPLOAD_CAPACITY` / `_REFILL` / `_PERIOD` / `_COST` | `5` / `5` / `PT1H` / `5` | Session bucket for `POST /invoices` (paid LLM extraction). |
| `RATELIMIT_UPLOAD_IP_CAPACITY` / `_IP_REFILL` / `_IP_PERIOD` | `10` / `10` / `PT1H` | IP ceiling for uploads. |
| `RATELIMIT_CHAT_CAPACITY` / `_REFILL` / `_PERIOD` | `20` / `20` / `PT1M` | Session bucket for `POST /assistant/chat`. |
| `RATELIMIT_CHAT_IP_CAPACITY` / `_IP_REFILL` / `_IP_PERIOD` | `60` / `60` / `PT1M` | IP ceiling for chat. |
| `RATELIMIT_ADMIN_CAPACITY` / `_REFILL` / `_PERIOD` | `5` / `5` / `PT1M` | Same numbers on both the IP (pre-auth) and token (post-auth) layers. |
| `RATELIMIT_PUBLIC_READ_CAPACITY` / `_REFILL` / `_PERIOD` | `60` / `60` / `PT1M` | Cheap reads (fail-open). |
| `RATELIMIT_DEFAULT_CAPACITY` / `_REFILL` / `_PERIOD` (+ `_IP_*`) | `30` / `30` / `PT1M` | Fallback for any unmapped `/api/v1/` route, with the same IP ceiling as the paid routes. |

> **Fail policy:** paid/security profiles (`UPLOAD`, `CHAT`, `ADMIN`) are fail-closed — a store outage returns `503`. `PUBLIC_READ` is fail-open. An actual breach returns `429`. See `RATELIMIT.md`.

---

## LLM observability

Per-call LLM telemetry is emitted by `TimedChatLanguageModel` (latency, tokens, USD cost) into two independently toggled sinks. Both may run at once; both are cheap when off. See [`OBSERVABILITY.md`](OBSERVABILITY.md).

| Variable | Default | Description |
|---|---|---|
| `LLM_METRICS_ENABLED` | `true` | Publishes `llm.call.duration`, `llm.calls`, `llm.tokens`, `llm.cost.usd` as Micrometer meters on the Actuator `/metrics` + `/prometheus` endpoints (management port). |
| `LLM_TRACING_ENABLED` | `false` | Exports one OTLP span per LLM call to an external Langfuse backend. Requires `LANGFUSE_HOST`; fails fast on startup if the host is blank while enabled. |
| `LANGFUSE_HOST` | *(empty)* | Base URL of the external Langfuse instance, e.g. `http://langfuse.internal:3000`. Spans are POSTed to `{host}/api/public/otel/v1/traces`. |
| `LANGFUSE_PUBLIC_KEY` | *(empty)* | Langfuse project public key — sent as the username of the OTLP HTTP Basic auth header. |
| `LANGFUSE_SECRET_KEY` | *(empty)* | Langfuse project secret key — sent as the password of the OTLP HTTP Basic auth header. Inject as a secret. |

The Langfuse **backend** is external shared infrastructure (its own namespace, Postgres/ClickHouse, internal-only ingress) — BillMind only references it by URL. When tracing is disabled, no OpenTelemetry SDK is built.
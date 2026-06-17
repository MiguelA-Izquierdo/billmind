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
| `KNOWLEDGE_SEARCH_MIN_VECTOR_SCORE` | `0.5` | Minimum cosine similarity (0–1) a vector candidate must have to enter the RRF fusion step. Filters out semantically weak matches before ranking. Lower values increase recall at the cost of precision. Tune per embedding model: AllMiniLM-L6-v2 typically scores lower than OpenAI or nomic-embed-text on Spanish content. |
| `KNOWLEDGE_SEARCH_MAX_RESULTS` | `5` | Maximum number of results returned per search query. |

> **Tuning tip:** Start at `0.5` with OpenAI or nomic-embed-text. Drop to `0.3` if you observe low recall with AllMiniLM-L6-v2. Raise to `0.6–0.7` to enforce higher precision in production.

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

## JWT (Phase 2)

JWT auth is scaffolded for Phase 2 (Milestone 7). The variables are present but not enforced in Phase 1.

| Variable | Example | Description |
|---|---|---|
| `JWT_SECRET` | `≥32 chars` | Signing secret — minimum 32 characters |
| `JWT_EXPIRATION` | `86400000` | Token TTL in milliseconds (default: 24 h) |
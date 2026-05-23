# BillMind — Docker

## Requirements

- Docker Desktop with Compose v2.20+

---

## Startup commands

| Scenario | `.env` requirement | Command |
|---|---|---|
| Ollama (local LLM) | `KAFKA_ENABLED=false` *(default)* | `docker compose --profile local-ai up -d` |
| Ollama + Kafka | `KAFKA_ENABLED=true` | `docker compose --profile local-ai --profile kafka up -d` |
| Cloud LLM + Kafka | `KAFKA_ENABLED=true` | `docker compose --profile kafka up -d` |

> Without `--profile local-ai` you **must** configure a cloud LLM provider in `docker-compose.yml` (see below), otherwise the app will fail to start.

> **Kafka:** `KAFKA_ENABLED` controls the startup check and the Kafka consumer. Set it to `true` in your `.env` when using `--profile kafka`, and keep it `false` (the default) otherwise. Starting the app without the kafka profile but with `KAFKA_ENABLED=true` will cause a startup failure.

---

## Services

| Service | Profile | Description |
|---|---|---|
| `postgres` | — | PostgreSQL 16 + pgVector |
| `app` | — | BillMind API (built locally) |
| `ollama` | `local-ai` | Local LLM server — only needed when `LLM_PROVIDER=ollama` |
| `kafka` | `kafka` | Kafka 3.9 KRaft — only needed when `KAFKA_ENABLED=true` |

> Embeddings (`AllMiniLM-L6-v2`) run inside the `app` container via ONNX runtime (bundled in the jar). Ollama is **not** required for vector search.

---

## Switching the LLM provider

Edit the `environment` block of the `app` service in `docker-compose.yml`:

```yaml
LLM_PROVIDER: gemini          # ollama | openai | anthropic | gemini | groq
GEMINI_API_KEY: AIza...       # add the variable for the chosen provider
```

---

## Rebuilding

```bash
# After code changes
docker compose --profile local-ai --profile kafka up -d --build app

# After docker-compose.yml changes
docker compose --profile local-ai --profile kafka up -d --force-recreate app
```

---

## Reset

```bash
# Full reset (deletes all data)
docker compose --profile local-ai --profile kafka down -v && docker compose --profile local-ai --profile kafka up -d

# Database only
docker compose down && docker volume rm billmind_pgdata && docker compose --profile local-ai --profile kafka up -d
```

---

## Kafka — external access

The broker runs two listeners. External producers on the host use `localhost:9092`; the `app` container uses `kafka:29092` (Docker-internal).

```
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```
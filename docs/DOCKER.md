# BillMind — Docker

## Requirements

- Docker Desktop with Compose v2.20+
- For the `local-ai` profile: at least ~6 GB free RAM and ~4 GB disk for the `llama3.2` model. The cloud-provider setups have no such requirement.

---

## Startup commands

| Scenario | `KAFKA_ENABLED` | Command |
|---|---|---|
| Ollama (local LLM) | `false` *(default)* | `docker compose --profile local-ai up -d` |
| Ollama + Kafka | `true` | `docker compose --profile local-ai --profile kafka up -d` |
| Cloud LLM | `false` *(default)* | `docker compose up -d` |
| Cloud LLM + Kafka | `true` | `docker compose --profile kafka up -d` |

> Without `--profile local-ai` you **must** configure a cloud LLM provider in `docker-compose.yml` (see [Switching the LLM provider](#switching-the-llm-provider)), otherwise the app will fail to start.

> **First start with Ollama is slow.** The `ollama-setup` container pulls `llama3.2` (~2 GB) on the first run; until that finishes the app cannot answer chat or extraction requests. Watch progress with `docker compose logs -f ollama-setup`. The `OLLAMA_TIMEOUT_SECONDS: 240` in the compose file accommodates the slower cold inference of a local model.

---

## Configuration: `.env` vs `docker-compose.yml`

Only **one** variable is read from your shell or a `.env` file in the project root: `KAFKA_ENABLED` (via `${KAFKA_ENABLED:-false}`). Everything else — DB credentials, `LLM_PROVIDER`, API keys, pgVector settings — is set directly in the `environment:` block of the `app` service in `docker-compose.yml`. To change a provider or key, **edit the compose file** (see below); putting those values in `.env` has no effect.

> **Kafka:** `KAFKA_ENABLED` controls the startup check and the Kafka consumer. Set it to `true` (in `.env` or your shell) when using `--profile kafka`, and keep it `false` (the default) otherwise. Starting the app without the kafka profile but with `KAFKA_ENABLED=true` will cause a startup failure.

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

## Verify it's running

The API is published on host port **8082** (the Actuator management port `8083` is intentionally **not** published — it stays unreachable from the host).

```bash
# Wait for the app to report healthy (liveness probe on the internal :8083 port)
docker compose ps

# Follow application logs until startup completes
docker compose logs -f app

# Chat UI (full upload + compare + chat flow)
open http://localhost:8082/chat/
```

> The `app` service has a healthcheck (Actuator liveness probe). It shows as `starting` for up to 90 s on first boot, then `healthy`. The probe targets only `livenessState`, so it does not depend on Kafka or the database being up.

If the app exits immediately, the most common causes are a missing cloud LLM provider (no `--profile local-ai` and no provider configured) or `KAFKA_ENABLED=true` without `--profile kafka`.

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
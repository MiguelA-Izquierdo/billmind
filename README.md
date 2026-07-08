# BillMind

> **Are you overpaying on your utility bills?** BillMind ingests your invoices, understands them semantically, and will soon tell you exactly how much you're being overcharged — and who offers a better deal.

An AI-powered REST API for utility invoice intelligence: PDF ingestion, hybrid AI classification, LLM-powered structured extraction, and price comparison against real market data (Milestone 2).

Built with **Spring Boot 3.5.0**, **Java 21**, and **LangChain4j 1.0.0**. Supports both **fully local AI** (Ollama, zero cloud dependencies) and **cloud providers** (Anthropic, OpenAI, Gemini, Groq) via a single env variable — your choice.

---

## What it does

```
PDF invoice
    │
    ▼
1. INGEST       — validates MIME type, extracts text from the PDF
    │
    ▼
2. CLASSIFY     — hybrid classifier identifies supply type (electricity, gas, water, telecoms)
                  and provider company; rejects non-supply documents
    │
    ▼
3. EXTRACT      — LangChain4j ChatModel extracts structured fields (price/kWh, contracted power,
                  billing period, totals) via typed prompt + JSON parsing; PII redacted before persisting
    │
    ▼
4. COMPARE  *   — cross-references your rates against current market data; LLM evaluates overpayment
    │
    ▼
5. RECOMMEND *  — suggests cheaper providers or tariffs based on your consumption profile
```

\* Roadmap — see [`docs/PLAN.md`](docs/PLAN.md).

---

## Why it's interesting

**Hybrid AI classifier that minimizes LLM calls**

```
PDF text
  │
  ├─ blank? ──────────────────► rejected (OTHER / DESCONOCIDA)
  │
  ▼
KeywordInvoiceClassifier ──── match? ──► LlmInvoiceClassifier.extractCompany()
  │                                              │
  │ no match                                     ▼
  ▼                                     InvoiceClassification(type, company)
LlmInvoiceClassifier.classify()
  │
  ▼
InvoiceClassification(type, company)
```

Keywords handle the obvious cases ("kWh", "REE" → electricity). The LLM is only called for ambiguous documents or company extraction — keyword matching eliminates the majority of LLM calls. Reduction rates are measured as part of the Milestone 6 evaluation harness.

**Swappable LLM provider — one env var**

```bash
LLM_PROVIDER=ollama      # 100% local, no API keys needed
LLM_PROVIDER=anthropic   # Claude Sonnet 4.6
LLM_PROVIDER=openai      # GPT-4o
LLM_PROVIDER=gemini      # Gemini 2.5 Flash
LLM_PROVIDER=groq        # Llama 3.3 70B (fast inference)
```

Each provider is a `@ConditionalOnProperty` Spring bean. Switching costs zero refactoring.

**Hexagonal Architecture + DDD**

```
Infrastructure → Application → Domain
```

`domain/` has zero dependencies on Spring, JPA, LangChain4j, or Lombok — only `java.*`. Every port is an interface; every adapter is replaceable. The architecture enforces clear ownership boundaries — essential when agents generate code that must stay coherent across a growing codebase.

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.0 |
| LangChain4j | 1.0.0 (BOM) |
| Ollama (local LLM + embeddings) | — |
| AllMiniLM-L6-v2 (embeddings, 384 dim) | via LangChain4j |
| PostgreSQL | 16 |
| pgVector extension (IVFFlat index) | — |
| Apache PDFBox | via LangChain4j |
| JUnit 5 + Mockito | via Spring Boot |
| TestContainers | 1.21.0 |
| JaCoCo | 0.8.12 |

---

## Architecture

```
src/main/java/dev/izquierdo/billmind/
├── _shared/                    # Cross-cutting concerns
│   ├── application/            # CommandBus, QueryBus
│   ├── domain/                 # DomainEvent, DomainEventPublisher, exceptions
│   └── infrastructure/
│       ├── auth/               # JwtAuthFilter, AdminRoutesService (external auth delegation)
│       ├── adapter/            # ExternalAuthAdapter — calls external /introspect
│       ├── dto/                # ErrorResponseDTO, SuccessResponseDTO
│       ├── llm/                # TimedChatLanguageModel, ModelPricingRegistry
│       ├── session/            # SessionContext, SessionFilter, SessionService
│       └── GlobalExceptionHandler
│
├── invoice/                    # Bounded Context: ingestion, extraction & persistence  [Active]
│   ├── domain/
│   │   ├── model/              # Invoice, InvoiceClassification, SupplyDomain, InvoiceFields, ...
│   │   └── port/               # InvoiceParser, InvoiceClassifier, InvoiceFieldExtractor,
│   │                           # InvoiceRepository, PiiRedactor
│   ├── application/
│   │   ├── usecase/            # UploadInvoiceUseCase, GetInvoiceUseCase, GetSessionInvoicesUseCase
│   │   ├── command/            # UploadInvoiceCommand, UploadInvoiceCommandHandler
│   │   └── query/              # GetInvoiceQuery, GetSessionInvoicesQuery, handlers
│   └── infrastructure/
│       ├── adapter/
│       │   ├── classifier/     # KeywordInvoiceClassifier, LlmInvoiceClassifier
│       │   ├── fieldextractor/ # ExtractionPromptBuilder, InvoiceFieldsValidator
│       │   ├── pii/            # HybridPiiRedactor, PiiPatterns
│       │   ├── HybridInvoiceClassifier
│       │   └── LlmInvoiceFieldExtractor
│       ├── config/             # ChatModelRolesConfig, per-provider beans
│       ├── controller/         # InvoiceController
│       └── persistence/        # InvoiceEntity, JpaInvoiceRepository
│
├── assistant/                  # Bounded Context: conversational RAG          [Complete — M5]
├── comparison/                 # Bounded Context: savings / comparison engine [Complete — M3]
├── knowledge/                  # Bounded Context: regulatory KB + retrieval   [Complete — M2]
└── market/                     # Bounded Context: market rate ingestion        [Complete — M2]
```

---

## Authentication model

**Anonymous endpoints (Phase 1):** every request carries a client-generated UUID in `X-Session-Id`. The backend uses it to correlate resources (invoices, conversations) but does not authenticate the caller.

**Admin endpoints:** BillMind never validates tokens itself. Authentication is fully delegated to an external user microservice. On each admin request `JwtAuthFilter` extracts the `Authorization: Bearer <token>` header and calls `GET <AUTH_EXTERNAL_URL>/introspect` on the external service. A `200` response authorises the request; any other response or connectivity error fails closed. Currently the only admin endpoint is `DELETE /api/v1/market-rates`. More admin routes are registered in `AdminRoutesService` without touching the filter.

**Milestone 7:** user authentication will extend the same delegation model to user-facing endpoints rather than adding local JWT validation to BillMind.

---

## Observability

Logs and in-process metrics are the current observability story for Phase 1 (single-instance deployment).

- **Logs** — infrastructure layer only; no invoice content, no PII, no credentials ever logged. `[PII]` prefix on all redaction-related lines for easy filtering.
- **LLM observability** — every LLM call is logged with operation, provider, model, latency and token counts (where the provider supports it). Micrometer replaces log-based metrics at Milestone 6.

Full reference (log levels, key log lines, validation thresholds, Micrometer roadmap) → [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).

---

## Quick Start

```bash
# Local AI (Ollama), no Kafka — the simplest start
docker compose --profile local-ai up -d

# Local AI (Ollama) + Kafka — requires KAFKA_ENABLED=true in .env
KAFKA_ENABLED=true docker compose --profile local-ai --profile kafka up -d

# Cloud LLM provider, no Kafka (edit LLM_PROVIDER and add the API key in docker-compose.yml)
docker compose up -d
```

Then open the chat UI at **http://localhost:8082/chat/**. First start with Ollama downloads the `llama3.2` model (~2 GB) before the app is usable.

See [`docs/DOCKER.md`](docs/DOCKER.md) for the full setup guide, profile reference, and provider switching instructions.

---

## Kubernetes deployment

The Kubernetes manifests and the CD pipeline that deploys BillMind to a cluster live in a **separate infrastructure repository**, not in this repo. This app repo builds and publishes the Docker image; the infra repo takes that image and deploys it (the standard two-pipeline app/infra split).

**To deploy on Kubernetes, follow the instructions in that repository:**

- **[MiguelA-Izquierdo/billmind-infra](https://github.com/MiguelA-Izquierdo/billmind-infra)** — k3s manifests (Kustomize), namespace, ConfigMap, Secret template, Deployment, Service and Ingress, plus the CD `Jenkinsfile`.

Do not add cluster manifests to this repository; keep infrastructure-as-code in `billmind-infra`.

---

## Docs

- Docker setup → [`docs/DOCKER.md`](docs/DOCKER.md)
- Configuration reference → [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)
- API reference → [`docs/API.md`](docs/API.md)
- Market module → [`docs/MARKET.md`](docs/MARKET.md)
- Assistant module (agentic tool calling) → [`docs/ASSISTANT.md`](docs/ASSISTANT.md)
- Test guide → [`docs/TESTING.md`](docs/TESTING.md)
- Observability → [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md)
- Roadmap → [`docs/PLAN.md`](docs/PLAN.md)
- Kubernetes deployment → [`MiguelA-Izquierdo/billmind-infra`](https://github.com/MiguelA-Izquierdo/billmind-infra) (separate infra repo)
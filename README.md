# BillMind

> **Are you overpaying on your utility bills?** BillMind ingests your invoices, understands them semantically, and will soon tell you exactly how much you're being overcharged — and who offers a better deal.

An AI-powered REST API for utility invoice intelligence: PDF ingestion, hybrid AI classification, LLM-powered structured extraction, and (coming soon) price comparison against real market data.

Built with **Spring Boot 3.5.0**, **Java 21**, and **LangChain4j 0.36.2**. Supports both **fully local AI** (Ollama, zero cloud dependencies) and **cloud providers** (Anthropic, OpenAI, Gemini, Groq) via a single env variable — your choice.

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
3. EXTRACT      — LangChain4j AiServices extracts structured fields (price/kWh, contracted power,
                  billing period, totals); PII redacted before persisting
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
  ├─ blank? ──────────────────► rejected (OTRO / DESCONOCIDA)
  │
  ▼
KeywordClassifier ──── match? ──► LlmClassifier.extractCompany()
  │                                       │
  │ no match                              ▼
  ▼                              InvoiceClassification(type, company)
LlmClassifier.classify()
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
LLM_PROVIDER=gemini      # Gemini 1.5 Pro
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
│       ├── dto/                # ErrorResponseDTO, SuccessResponseDTO
│       ├── llm/                # TimedChatLanguageModel, ModelPricingRegistry
│       ├── session/            # SessionContext, SessionFilter, SessionService
│       └── GlobalExceptionHandler
│
├── invoice/                    # Bounded Context: ingestion, extraction & persistence  [Active]
│   ├── domain/
│   │   ├── model/              # Invoice, InvoiceClassification, InvoiceType, InvoiceFields, ...
│   │   └── port/               # InvoiceParser, InvoiceClassifier, InvoiceFieldExtractor,
│   │                           # InvoiceRepository, PiiRedactor
│   ├── application/
│   │   ├── usecase/            # UploadInvoiceUseCase, GetInvoiceUseCase, GetSessionInvoicesUseCase
│   │   ├── command/            # UploadInvoiceCommand, UploadInvoiceCommandHandler
│   │   └── query/              # GetInvoiceQuery, GetSessionInvoicesQuery, handlers
│   └── infrastructure/
│       ├── adapter/
│       │   ├── classifier/     # HybridInvoiceClassifier, KeywordClassifier, LlmInvoiceClassifier
│       │   ├── fieldextractor/ # LlmInvoiceFieldExtractor, ExtractionPromptBuilder
│       │   └── pii/            # HybridPiiRedactor, PiiPatterns
│       ├── config/             # LangChain4jConfig, ChatModelRolesConfig, per-provider beans
│       ├── controller/         # InvoiceController
│       └── persistence/        # InvoiceEntity, JpaInvoiceRepository
│
├── assistant/                  # Bounded Context: conversational RAG        [Roadmap M3]
├── comparison/                 # Bounded Context: price comparison agent     [Roadmap M5]
└── market/                     # Bounded Context: market rate ingestion      [Roadmap M4]
```

---

## Observability

Logs and in-process metrics are the current observability story for Phase 1 (single-instance deployment).

- **Logs** — infrastructure layer only; no invoice content, no PII, no credentials ever logged. `[PII]` prefix on all redaction-related lines for easy filtering.
- **LLM observability** — every LLM call is logged with operation, provider, model, latency and token counts (where the provider supports it). Micrometer replaces log-based metrics at Milestone 6.

Full reference (log levels, key log lines, validation thresholds, Micrometer roadmap) → [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).

---

## Quick Start

```bash
# 1. Start infrastructure
#    Default (cloud provider or Ollama already running locally):
docker-compose up -d

#    Local AI mode — also starts Ollama and pulls all-minilm automatically:
docker-compose --profile local-ai up -d

# 2. Configure environment
cp .env.example .env
# Edit .env — set LLM_PROVIDER and the matching credentials

# 3. Run
./mvnw spring-boot:run
# → http://localhost:8082
```

- Full configuration reference → [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)
- API reference → [`docs/API.md`](docs/API.md)
- Test guide → [`docs/TESTING.md`](docs/TESTING.md)
- Observability → [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md)
- Roadmap → [`docs/PLAN.md`](docs/PLAN.md)
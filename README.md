# BillMind

An AI-powered REST API that ingests utility invoices (electricity, gas, water, telecoms) in PDF format, classifies them, and — once the comparison module is complete — evaluates whether the price can be lowered by cross-referencing semantically similar invoices stored in a vector database.

Built with **Spring Boot 3.2.5**, **Java 21**, and a fully local AI stack (Ollama + LangChain4j). No external AI services required.

---

## What it does

1. **Ingest** — accepts a PDF invoice via REST.
2. **Classify** — a hybrid classifier (keyword-first, LLM fallback) determines the supply type (electricity, gas, water, telecoms) and the provider company. Non-supply documents are rejected.
3. **Vectorize** — the invoice is split into semantic chunks, each converted to a 384-dim embedding with AllMiniLM-L6-v2 and stored in PostgreSQL + pgVector (HNSW index).
4. **Market sync** *(roadmap)* — a daily cron job fetches current tariff and pricing data from utility providers and persists it in the database, keeping market information fresh.
5. **Compare** *(roadmap)* — given an uploaded invoice, the system cross-references the user's rates against the latest market data and uses an LLM to evaluate whether the user is overpaying and by how much.
6. **Recommend** *(roadmap)* — based on the comparison result, suggest cheaper alternative providers or tariffs.

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring Data JPA | (included in Boot) |
| LangChain4j | 0.33.0 |
| Ollama (local LLM + embeddings) | — |
| AllMiniLM-L6-v2 (embeddings, 384 dim) | 0.33.0 |
| PostgreSQL | 16 |
| pgVector (HNSW index) | — |
| Apache PDFBox (PDF parsing) | (via LangChain4j) |
| JUnit 5 + Mockito | (included in Boot) |
| TestContainers | 1.21.0 |
| JaCoCo (coverage) | 0.8.12 |

---

## Architecture

Hexagonal Architecture (Ports & Adapters) + DDD, organized into bounded contexts:

```
src/main/java/com/demo/billmind/
├── _shared/                    # Cross-cutting concerns
│   ├── application/            # CommandBus, PropertyExtractorService
│   ├── domain/                 # DomainEvent, DomainEventPublisher, exceptions
│   └── infrastructure/         # GlobalExceptionHandler, response DTOs, PDF utilities
│
├── invoice/                    # Bounded Context: Invoice ingestion & vectorization
│   ├── domain/
│   │   ├── model/              # Invoice, InvoiceChunk, InvoiceClassification, InvoiceType
│   │   └── port/               # InvoiceParser, InvoiceClassifier, InvoiceChunkRepository
│   ├── application/
│   │   ├── usecase/            # UploadInvoiceUseCase
│   │   └── command/            # UploadInvoiceCommand, UploadInvoiceCommandHandler
│   └── infrastructure/
│       ├── adapter/            # PdfInvoiceParser, PgVectorInvoiceRepository
│       │   └── classifier/     # HybridInvoiceClassifier, KeywordInvoiceClassifier, LlmInvoiceClassifier
│       ├── config/             # LangChain4jConfig, ApplicationUseCaseConfig
│       └── controller/         # InvoiceController + InvoiceUploadResponse DTO
│
├── comparison/                 # (roadmap) Invoice price comparison
└── market/                     # (roadmap) Provider recommendations
```

**Dependency rule (never violated):**

```
Infrastructure → Application → Domain
```

`domain/` has zero dependencies on Spring, JPA, LangChain4j, or Lombok — only `java.*`.

### Hybrid Invoice Classifier

The classifier uses a two-stage strategy to minimize LLM calls:

```
PDF bytes
   │
   ▼
PdfTextExtractor
   │
   ├─ text blank? ──► InvoiceClassification(OTRO, "DESCONOCIDA")
   │
   ▼
KeywordInvoiceClassifier   ──── match? ──► LlmInvoiceClassifier.extractCompany()
   │                                              │
   │ no match                                     ▼
   ▼                                    InvoiceClassification(type, company)
LlmInvoiceClassifier.classify()
   │
   ▼
InvoiceClassification(type, company)
```

If keywords confidently identify the type (e.g. "REE", "kWh" → `LUZ`), only the company extraction is delegated to the LLM. Full LLM classification is only used as a fallback.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Java 21 | |
| Maven | or use `./mvnw` wrapper |
| Docker + Docker Compose | for PostgreSQL + pgVector |
| [Ollama](https://ollama.ai) | runs locally on port 11434 |

Pull the required models before starting:

```bash
ollama pull all-minilm
ollama pull llama3
```

---

## Configuration

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

| Variable | Example | Description |
|---|---|---|
| `SERVER_PORT` | `8082` | API port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/billmind` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `billmind` | DB user |
| `DB_PASSWORD` | `billmind` | DB password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama base URL |
| `OLLAMA_EMBEDDING_MODEL` | `all-minilm` | Embeddings model |
| `OLLAMA_CHAT_MODEL` | `llama3` | Chat/classification model |
| `PGVECTOR_TABLE_NAME` | `vector_store` | pgVector table name |
| `PGVECTOR_DIMENSIONS` | `384` | Embedding dimensions (must match model) |
| `PGVECTOR_INDEX_TYPE` | `HNSW` | pgVector index type |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:3000` | Allowed CORS origins |
| `JWT_SECRET` | `a-secret-of-at-least-32-characters` | JWT signing secret |
| `JWT_EXPIRATION` | `86400000` | JWT TTL in milliseconds (24 h) |

---

## Deployment

### Docker Compose (recommended)

Start the PostgreSQL + pgVector container:

```bash
docker-compose up -d
```

Then run the application:

```bash
./mvnw spring-boot:run
# API available at http://localhost:8082
```

### Local (no Docker)

Requires a running PostgreSQL 16 instance with the `pgvector` extension enabled, and Ollama running locally.

```bash
./mvnw spring-boot:run
```

---

## API

Full interactive documentation is available via Spring Boot Actuator at `/actuator/health`.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/invoices/upload` | Upload a PDF invoice — classifies, chunks, and vectorizes it |

### Response format

```json
{ "status": "success", "data": { ... } }
{ "status": "error",   "message": "...", "errors": { "campo": "mensaje" } }
```

### Upload example

```bash
curl -X POST http://localhost:8082/api/v1/invoices/upload \
  -F "file=@factura_luz.pdf"
```

**201 Created**
```json
{
  "status": "success",
  "data": {
    "invoiceId": "a1b2c3d4-...",
    "fileName": "factura_luz.pdf"
  }
}
```

**422 Unprocessable Entity** — document is not a supply invoice (e.g. a bank receipt):
```json
{
  "status": "error",
  "message": "El documento no es una factura de suministro reconocida."
}
```

---

## Tests

```bash
# Unit tests only (no Docker required)
./mvnw test

# Unit + integration tests (requires Docker for TestContainers)
./mvnw verify

# Coverage report
./mvnw verify
# → target/site/jacoco/index.html
```

| Layer | Type | Annotations |
|---|---|---|
| `domain/` | Pure unit test | `@Test` only |
| `application/usecase/` | Unit test with Mockito | `@ExtendWith(MockitoExtension.class)` |
| `infrastructure/adapter/` | Integration test | `@SpringBootTest` + TestContainers |
| `infrastructure/controller/` | Integration test | `@SpringBootTest` + `@AutoConfigureMockMvc` |

Integration tests are suffixed `*IT.java` and excluded from `mvn test` by default.

---

## Roadmap

| Module | Status | Description |
|---|---|---|
| `invoice/` | **Active** | PDF ingestion, hybrid AI classification, semantic vectorization |
| `comparison/` | Scaffolding | Cross-reference user invoice rates against current market data; LLM evaluates overpayment |
| `market/` | Scaffolding | Daily cron syncs tariff/pricing data from providers into DB; feeds the comparison module |
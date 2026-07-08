# BillMind — Observability

Reference for logs, metrics, and future monitoring integration.

---

## Logging

### Conventions

- All logging lives in the **infrastructure layer** only — never in domain or application.
- Logs **never contain invoice content**, PII fragments, JWT tokens, or credentials.
- Exception messages are not logged directly (may contain input fragments); only `e.getClass().getSimpleName()` is logged.
- Sensitive operations use the `[PII]` prefix for easy log filtering.

### Log levels

| Level | When |
|---|---|
| `DEBUG` | Normal operation details (chars processed, path taken) |
| `INFO` | Classification results, LLM provider selected |
| `WARN` | Recoverable failures — LLM error, fallback triggered, invalid response |
| `ERROR` | Unrecoverable failures handled by `GlobalExceptionHandler` |

### Key log lines

```
# PII redactor — LLM path taken
DEBUG [PII] LLM redaction applied to header (1843 chars)

# PII redactor — LLM response rejected, safe fallback
WARN  [PII] LLM response rejected (len=12 vs input=1843), falling back to regex-only

# PII redactor — LLM call failed, safe fallback
WARN  [PII] LLM redaction failed (HttpTimeoutException), keeping regex-only result

# Classifier — keyword shortcut taken (no LLM call)
INFO  Keyword classification → type=ELECTRICITY, company=IBERDROLA

# Classifier — LLM required
INFO  LLM classification → type=TELECOM, company=MOVISTAR
```

---

## Metrics

### LLM response validation thresholds

`HybridPiiRedactor` rejects an LLM response (triggers fallback) when:

- Response is blank.
- `response.length() < input.length() * 0.4` — suspiciously short, likely truncated.
- `response.length() > input.length() * 2.0` — suspiciously long, likely hallucinated text.
- Response starts with a conversational prefix (`"here is"`, `"aquí"`, `"claro"`) — model ignored the no-preamble instruction.

### Timeout

LLM call timeout is configured at the `ChatModel` bean level, not inside `HybridPiiRedactor`. See `infrastructure/config/chat/` for the per-provider configuration (e.g., `.timeout(Duration.ofSeconds(240))` on the LangChain4j builder).

---

## LLM call observability — `TimedChatLanguageModel`

Every `ChatModel` bean is wrapped with `TimedChatLanguageModel` (Decorator pattern) inside `ChatModelRolesConfig`. Adapters never instrument LLM calls themselves — the decorator handles it transparently.

### How it works

`ChatModelRolesConfig` exposes two semantic role beans:

| Bean | Role tag | Intended use |
|---|---|---|
| `fastChatModel` | `fast` | Low-latency tasks: classification, PII redaction |
| `smartChatModel` | `smart` | Quality-sensitive tasks: field extraction, RAG, reasoning |

Both beans are instances of `TimedChatLanguageModel(delegate, role, provider, model)`. On every `chat()` call the decorator:

1. Resolves the **operation** name — reads `MDC.get("llm.operation")` if set by the caller; otherwise walks the stack trace and picks the first frame inside `dev.izquierdo.billmind` that is not the decorator itself (e.g. `LlmInvoiceFieldExtractor.extract`). Callers never need to manage MDC unless they want an explicit label.
2. Delegates to the real model.
3. Logs a single structured `INFO` line with all fields (see format below).
4. Re-throws any exception unchanged after logging the error class.

`TimedChatLanguageModel` overrides `chat(ChatRequest)` and delegates via `delegate.chat(request)`. `doChat(ChatRequest)` is also implemented as a passthrough (required since the interface declares it abstract), but it is never reached from the `chat(String)` → `chat(ChatRequest)` call chain.

### Log format

```
[LLM]  operation=<caller>  [type=<mdcType>]  role=<fast|smart>  provider=<openai|anthropic|groq|gemini|ollama>  model=<model-id>  latency=<N>ms  tokensIn=<N>  tokensOut=<N>  tokensTotal=<N>  [costUsd=<N.NNNNNN>]  [error=<ExceptionClass>]
```

- `type` — optional, set by caller via `MDC.put("llm.type", "…")` (e.g. `"redaction"`, `"classification"`).
- `costUsd` — only present when the model is in `ModelPricingRegistry` (cloud providers). Ollama (local) is omitted.
- `error` — only present on failure.

Example:
```
[LLM]  operation=LlmInvoiceFieldExtractor.extract  role=smart  provider=openai  model=gpt-4o-mini  latency=1342ms  tokensIn=512  tokensOut=128  tokensTotal=640  costUsd=0.000153
```

### `ModelPricingRegistry`

Package-private class (`_shared/infrastructure/llm/`) that holds approximate USD list prices per 1M tokens for all supported cloud models. Used exclusively by `TimedChatLanguageModel` to compute `costUsd` in the log line. Update this registry when adding a new provider or when prices change — it has no effect on routing or behaviour.

---

> For the RAGAS-style LLM/RAG **quality** gate (faithfulness, context precision, answer
> relevancy over a golden set), see `@docs/EVAL.md`. This section covers runtime metrics only.

## Micrometer integration (Milestone 6)

`spring-boot-starter-actuator` + `micrometer-registry-prometheus` are on the classpath, and
`management.endpoints.web.exposure.include` exposes `health,info,metrics,prometheus`. The
custom `Counter` and `Timer` instruments below are wired and live.

Actuator is exposed on a separate, internal-only management port (`8083` by default,
override with `MANAGEMENT_PORT`), so the endpoints below are reached on that port — not
the application port (e.g. `http://localhost:8083/actuator/prometheus`).

Metrics exposed via `/actuator/metrics` and `/actuator/prometheus` (on the management port):

| Metric | Type | Tags | Emitted by |
|---|---|---|---|
| `pii.llm.invocations` | Counter | — | `HybridPiiRedactor` |
| `pii.llm.failures` | Counter | `reason` (exception class) | `HybridPiiRedactor` |
| `pii.llm.fallbacks` | Counter | `reason` (exception / invalid_response) | `HybridPiiRedactor` |
| `invoice.classify.duration` | Timer | `strategy` (keyword / llm) | `HybridInvoiceClassifier` |
| `invoice.upload.duration` | Timer | — | `InvoiceController` |
| `llm.call.duration` | Timer | `role`, `provider`, `model`, `operation`, `outcome` | `MetricsLlmTelemetry` |
| `llm.calls` | Counter | `role`, `provider`, `model`, `operation`, `outcome` | `MetricsLlmTelemetry` |
| `llm.tokens` | Counter (by count) | `role`, `provider`, `model`, `direction` (input/output) | `MetricsLlmTelemetry` |
| `llm.cost.usd` | Counter (by USD) | `role`, `provider`, `model` | `MetricsLlmTelemetry` |

The `llm.*` meters are fed by the same per-call hook as the `[LLM]` log line — `TimedChatLanguageModel`
builds one `LlmCallData` and fans it out to every active telemetry sink. They are on by default
(`LLM_METRICS_ENABLED=true`); token counts and cost are deliberately kept off the tag set to bound
cardinality. Turn the sink off with `LLM_METRICS_ENABLED=false`.

An LLM exception increments both `pii.llm.failures` (tagged with the exception class) and
`pii.llm.fallbacks{reason=exception}`; a rejected/invalid LLM response increments only
`pii.llm.fallbacks{reason=invalid_response}`. Instrumentation lives exclusively in the
infrastructure layer, consistent with the logging convention above.

---

## LLM tracing → Langfuse (OTLP)

The same `TimedChatLanguageModel` hook can also export **one OpenTelemetry span per LLM call** to a
Langfuse backend over OTLP/HTTP. This is the distributed-tracing complement to the aggregate
`llm.*` metrics above: metrics answer "how many calls / tokens / dollars", traces answer "what did
*this* call look like".

### Telemetry sinks

Both sinks consume the same `LlmCallData`; each has its own flag, so a deployment runs neither,
either, or both. When both flags are off, `TimedChatLanguageModel` holds `LlmTelemetry.NOOP` and no
SDK is built.

| Sink | Class | Flag (default) | Destination |
|---|---|---|---|
| Metrics | `MetricsLlmTelemetry` | `LLM_METRICS_ENABLED` (`true`) | Micrometer → Actuator `/metrics`, `/prometheus` |
| Tracing | `TracingLlmTelemetry` | `LLM_TRACING_ENABLED` (`false`) | OpenTelemetry SDK → OTLP → Langfuse |

`ChatModelRolesConfig` composes whatever sink beans exist into one `LlmTelemetry` and injects it into
both role models. `LlmTracingConfig` (`@ConditionalOnProperty`) builds the OTel SDK, OTLP exporter
and `Tracer` only when tracing is enabled.

### Backend is external

The Langfuse **backend** is shared infrastructure deployed outside this repo (its own namespace,
Postgres/ClickHouse, internal-only ingress) — exactly like Postgres, Kafka and Ollama. BillMind only
references it by `LANGFUSE_HOST` and authenticates with project keys injected as secrets. Spans are
sent to `{LANGFUSE_HOST}/api/public/otel/v1/traces` with an HTTP Basic header built from
`LANGFUSE_PUBLIC_KEY:LANGFUSE_SECRET_KEY`. Enabling tracing with a blank host fails fast at startup.

### Span shape (OpenTelemetry GenAI conventions)

Since the sink runs after the call completes, the span is created with an explicit start timestamp
and closed with the matching end timestamp, so its duration equals the real call latency.

| Attribute | Source |
|---|---|
| span name | `gen_ai <operation>` (e.g. `gen_ai LlmInvoiceFieldExtractor.extract`) |
| `gen_ai.system` | provider (`openai`, `anthropic`, `groq`, `gemini`, `ollama`) |
| `gen_ai.request.model` | model id |
| `gen_ai.usage.input_tokens` / `output_tokens` / `total_tokens` | `TokenUsage` (omitted when absent) |
| `gen_ai.usage.cost` | USD from `ModelPricingRegistry` (omitted when unpriced) |
| `llm.role` / `llm.operation` / `llm.type` | role bean, resolved caller, optional MDC type |
| status `ERROR` + `error.type` | on a failed call |

A broken exporter never breaks inference: `TimedChatLanguageModel` swallows and logs any telemetry
failure rather than letting it escape onto the LLM path.
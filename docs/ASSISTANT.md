# BillMind — Assistant Module

The `assistant` bounded context is the conversational layer: an anonymous visitor uploads an
invoice and chats about it. Answers combine the user's own invoice data with the regulatory
knowledge base and the current market. This document covers how the assistant assembles context
and, from iteration 2 onwards, **why** it does so through agentic tool calling rather than
loading everything up front.

> For the RAG/generation **quality** gate (faithfulness, context precision, answer relevancy),
> see [`EVAL.md`](EVAL.md). For LLM runtime logging/metrics, see [`OBSERVABILITY.md`](OBSERVABILITY.md).

---

## Request flow (unchanged across modes)

```
POST /api/v1/assistant/chat  (X-Session-Id, { message, invoiceId?, conversationId? })
  → AssistantController → ChatUseCase.execute(ChatCommand)
       ├─ ConversationService.resolve()        # conversationId handshake, in-memory, TTL + max-size
       ├─ ChatContextAssembler.assemble()       # builds ChatContext (mode-dependent — see below)
       ├─ AssistantLlmPort.answer(context, question, history)   # 1 of 2 adapters (see below)
       └─ ConversationService.recordExchange()  # persists the turn for multi-turn continuity
  → ChatResult (conversationId, answer, citations)   # streamed to the browser via SSE
```

`ChatUseCase` depends only on the **port** `AssistantLlmPort`, so the two adapters below are
swapped transparently — the use case, controller, SSE publisher and conversation store never
change. This is the whole point of keeping the port hexagonal.

---

## The problem iteration 2 solves

The original (eager) assistant assembled a **static, unconditional** context on every question:
`ChatContextAssembler` always loaded the invoice **plus every market tariff plus the comparison
plus a regulatory RAG search**, and `LlmAssistantAdapter` dumped all of it into the system
prompt. That has three concrete problems:

1. **Noise & cost.** Market rates and regulatory snippets were sent even when the question did
   not need them (e.g. "what does CUPS mean?" still shipped the full tariff table). More input
   tokens, higher latency, higher bill — every turn.
2. **The LLM re-ranks unreliably.** With the raw tariff list in front of it, the model was
   tempted to rank tariffs itself and compute "savings", which LLMs do badly. (Iteration 1
   already precomputed a deterministic comparison to lean on; iteration 2 removes the raw list
   from the default prompt entirely.)
3. **No directed querying.** There was no way to ask a targeted question — "what does Naturgy
   offer?", or filter tariffs by company — because retrieval was fixed and question-agnostic.

**The fix:** make the assistant *agentic*. Inline only the user's invoice; expose the three
retrieval capabilities as **tools** and let the LLM decide which to call based on the question.

---

## Two modes, one flag

Selected at startup by `assistant.tools.enabled` (env `ASSISTANT_TOOLS_ENABLED`, default
`false`). The two adapters are mutually exclusive Spring beans via `@ConditionalOnProperty`:

| `assistant.tools.enabled` | Adapter | Assembler behaviour |
|---|---|---|
| `false` *(default)* | `LlmAssistantAdapter` (eager) | Loads invoice + market + comparison + RAG up front |
| `true` | `AgenticAssistantLlmAdapter` (tools) | Loads **only** the invoice; the rest via tools |

### Why a flag with a fallback (not a hard switch)

Tool calling requires a **tool-capable** model. Cloud models (OpenAI, Anthropic, Groq's
`openai/gpt-oss-120b`, Gemini) support it; small local Ollama models are unreliable at it.
Keeping the eager adapter as the default is a safety net: BillMind still works end-to-end on any
model. Turn the flag on only when `smartChatModel` is known to be tool-capable.

### Why a manual tool-calling loop (not `AiServices` / `@Tool`)

LangChain4j offers a high-level `AiServices` + `@Tool` abstraction. We deliberately run a
**low-level manual loop** instead, because it keeps us compatible with the pieces we already own:

- **`TimedChatLanguageModel` instrumentation.** Tool specs travel *inside* the `ChatRequest`, so
  the existing decorator logs every round (latency, tokens, cost) with no changes. `AiServices`
  would hide the round-trips behind its own machinery.
- **Hexagonal ports.** The tools call the same `ComparisonContextPort`, `MarketRatesContextPort`
  and `RegulationSearchPort` the eager path uses — no duplicated retrieval logic, no domain
  leakage.
- **Precise citations.** We accumulate only the regulatory snippets actually retrieved during
  the turn (see below), which needs control over the loop.

---

## Agentic adapter — the loop

`AgenticAssistantLlmAdapter` runs, per turn:

```
messages = [ System(rules + invoice inline), ...history..., User(question) ]
citationSink = []
for round in 0..MAX_TOOL_ROUNDS (5):
    ai = smartChatModel.chat( messages + toolSpecifications )   # maxOutputTokens = 400
    messages.add(ai)
    if ai has NO tool requests:            → return answer(ai.text, citations)
    for each tool request:
        result = AssistantTools.dispatch(req, invoiceFields, citationSink)
        messages.add( ToolExecutionResultMessage.from(req, result) )
# rounds exhausted → one final call WITHOUT tools to force a textual answer
```

- **`MAX_TOOL_ROUNDS = 5`** bounds the LLM↔tools ping-pong so a model that keeps requesting
  tools can never loop forever; the final tool-less call guarantees a textual answer.
- **The `AiMessage` with tool requests is added to `messages` before its results.** The tool
  protocol requires the request message to precede its `ToolExecutionResultMessage`, or the
  provider rejects the next call.
- **`ChatResult.conversationId` is `null` here** and filled by `ChatUseCase` afterwards.

### LangChain4j 1.0.0 API note

Tool specs and `maxOutputTokens` must be set **inside** `ChatRequestParameters.builder()`, not as
top-level shortcuts on `ChatRequest.builder()`. Mixing `.parameters(...)` with the top-level
shortcuts throws a validation exception. Confirmed against `langchain4j-core:1.0.0`.

---

## The tools

Built and dispatched by `AssistantTools` (`infrastructure/adapter/tool/`), only wired when the
flag is on. The catalogue advertised to the model each round:

| Tool | Params | Backing port | When the model should call it |
|---|---|---|---|
| `get_invoice_comparison` | *(none)* | `ComparisonContextPort.summarize(fields)` | "Am I paying too much? / which tariff is cheaper?" |
| `search_market_rates` | `company` *(optional, string)* | `MarketRatesContextPort.loadLatestRates(domain)` | Questions about a specific company or tariff |
| `search_regulation` | `query` *(required, string)* | `RegulationSearchPort.search(query, n)` | Concepts, regulation, invoice-term definitions |
| `search_invoice_text` | `query` *(required, string)* | `InvoiceTextSearch` over `ChatContext.invoiceText` | Any figure on the user's own bill that is not one of the extracted fields |

- **`get_invoice_comparison` takes no params** because the comparison is a deterministic function
  of the user's own invoice. The tool exists so the model *explains* the precomputed result
  instead of ranking raw rates — the reliability concern from problem #2.
- **`search_market_rates` filters by `company` in memory** (case-insensitive substring). The seed
  dataset is small, so no repository/port change was needed — just a stream filter after
  `loadLatestRates(domainOf(fields))`.
- **`search_regulation` requires `query`** — the model formulates the semantic search string from
  the user's question. Uses `knowledge.search.default-max-results` (default 5), the same knob the
  eager path uses.
- **`search_invoice_text` requires `query`** — see below.

Argument JSON is parsed tolerantly with Jackson: a missing key, blank JSON, or malformed input is
treated as "argument absent" rather than failing the turn.

### `search_invoice_text` — reaching the lines the field set leaves out

`ElectricityFields` keeps a fixed subset of the bill, so the power term in €/kW/day, the electricity
tax, the meter rental, discounts and any retailer-specific charge exist **only** in
`rawTextRedacted`. Those are ordinary questions a user asks in front of their bill, and before this
tool the assistant answered that it did not have the figure.

The text is not inlined. `ChatContextAssembler` already loads the whole `Invoice`, so it now carries
`rawTextRedacted` on `ChatContext` unformatted, and only a *searched fragment* ever reaches a prompt.
That is the whole point of making it a tool: inlining the document would cost the same tokens on
every turn of every conversation and would not fit a large PDF at all, while a search returns a
handful of lines and costs nothing on the turns nobody asks.

`InvoiceTextSearch` is deliberately keyword-based and dependency-free:

1. **Fold** query and lines to lowercase without diacritics, so `eléctrico` matches `ELECTRICO` —
   which is how OCR usually renders it.
2. **Stem** each query word to its first 6 characters, so `contador` also finds `contadores`.
   Words shorter than 4 characters and a short stop-word list are dropped: they match every line and
   rank nothing.
3. **Score** each line by how many distinct stems it contains. Requiring *all* terms would miss
   `ALQUILER EQUIPOS DE MEDIDA` for the query "alquiler contador"; accepting *any* would return
   noise. Scoring keeps the partial match and ranks the better one higher.
4. **Cap and reorder** — the best 10 lines, capped at 3 000 characters, then re-sorted into document
   order so a multi-line block still reads top to bottom. Relevance decides what survives; position
   decides how it prints. This cap is what bounds the context cost regardless of PDF size.

Two deliberate non-features. There is **no fuzzy matching**: an invoice is full of codes and numbers,
and edit-distance tolerance trades "finds nothing" for "finds the wrong line", which is worse because
the model believes it. And there is **no embedding step**: the concepts are named almost verbatim in
the question, and the model has already rewritten the user's wording — typos included — into the
search terms before this runs.

A miss returns a literal stating that the words were not found and that this does **not** mean the
charge is absent, plus alternative wordings to retry with. Same reasoning as `notInCatalogue`: a bare
"no results" gets reported to the user as a fact about their bill.

The result is **not cached** — unlike `search_regulation`, it is a function of *this* invoice, so it
is neither argument-deterministic nor shareable across sessions.

### Citation accuracy

Only `search_regulation` feeds the `citationSink`, and only with the snippets it actually
retrieved. So `ChatResult.citations` reflects exactly the regulatory sources used *this turn* —
not a fixed RAG dump. A comparison-only or market-only turn correctly returns **zero** citations.

---

## Shared formatting

`AssistantContextFormatter` holds the Spanish `€`/`kWh` formatters (`formatFields`,
`formatMarketRates`, `formatComparison`, `num`, `eur`), extracted from the original adapter so
both the eager prompt and the agentic tool-dispatch render numbers identically (comma decimal
separator, Spanish locale). No duplicated formatting logic across the two modes.

---

## System prompt (tools mode)

Distinct from the eager template: it contains the invoice data inline (inside a `PromptFence`), the
rules (including the "respond in Spanish" instruction, per the project language convention), and
guidance on *when* to use each tool. It has **no** eager market/comparison/regulatory sections — the
model sees the tool specs through the tool-calling protocol itself, not through prompt text.

Tool results are fenced uniformly in `AssistantTools.dispatch`, and the eager adapter keeps its four
context sections out of the `system` role entirely — they travel in the user message, each fenced,
followed by a trailing instruction block. The tools path gets that role separation for free, since a
tool result is already its own message. See ARCHITECTURE Design Decision #14.

The rules describe *situations*, never tool names. Naming the tools in the prompt turned them into
vocabulary the model shared with the user: `llama-3.3-70b-versatile` answered "te recomiendo que
utilices la herramienta `get_invoice_comparison`" and then asked permission to call it, burning a
whole turn. Three rules enforce the boundary: tools are an internal mechanism and are never
mentioned; the model acts instead of asking permission and never closes an answer offering to look
something up; and missing data is stated as a limit of BillMind's catalogue, never as a fact about
the world.

---

## Configuration

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `assistant.tools.enabled` | `ASSISTANT_TOOLS_ENABLED` | `false` | `true` → agentic adapter; `false` → eager adapter |
| `assistant.tools.cache.ttl` | `ASSISTANT_TOOLS_CACHE_TTL` | `PT2H` | Write-based TTL for the `search_regulation` result cache |
| `assistant.tools.cache.max-size` | `ASSISTANT_TOOLS_CACHE_MAX_SIZE` | `500` | Max cached regulatory searches before size eviction |
| `knowledge.search.default-max-results` | `KNOWLEDGE_SEARCH_MAX_RESULTS` | `5` | Snippets per `search_regulation` / eager RAG |
| `assistant.conversation.max-size` | `ASSISTANT_CONVERSATION_MAX_SIZE` | `1000` | In-memory conversation cap (LRU eviction) |
| `assistant.conversation.ttl` | `ASSISTANT_CONVERSATION_TTL` | `PT2H` | Sliding TTL for idle conversations |

---

## Observability

Per turn (agentic mode):

- **INFO** — `[AGENT][round=N] tool calls requested: [...]` (which tools fired, no PII) and
  `[AGENT] answer produced (N citations)`. Plus one `[LLM] ... role=smart ...` line per round from
  `TimedChatLanguageModel` (latency, tokens, cost).
- **DEBUG** — `[TOOL] dispatch name=... arguments=...` (from `AssistantTools`) and
  `[AGENT] answer text: ...` (the full answer may contain invoice figures, so it stays at DEBUG,
  consistent with rule 6: INFO logs must not carry invoice content).

Enable `logging.level.dev.izquierdo.billmind.assistant.infrastructure.adapter=DEBUG` to see tool
arguments and the answer text during testing.

---

## Known behaviour & future work

- **Redundant tool calls & invalid tool calls (mitigated).** Some models (observed with the
  now-retired `llama-3.3-70b-versatile`) occasionally request the *same* tool with the *same* arguments twice
  before answering, and sometimes emit a malformed tool call that the provider rejects with
  `400 tool_use_failed`. `AgenticAssistantLlmAdapter` now defends on three fronts:
  - **Short-circuit** — a within-turn `servedThisTurn` set; when a round contains only already-seen
    calls, the loop stops offering tools and forces the final tool-less answer (one fewer LLM round,
    smaller surface for a malformed call).
  - **Resilience** — an `InvalidRequestException` on a tool round degrades to a tool-less retry;
    any further failure returns a Spanish fallback message. The SSE stream never propagates the 400.
  - **Cache** — `search_regulation` (the only argument-deterministic, invoice-independent tool) is
    memoized via `ToolResultCache` (Caffeine today, Redis-backed later), so repeats within and
    across turns skip the hybrid retrieval. Cached citations are re-added so answers keep sources.
- **Non-existence inferred from missing data (mitigated).** Asked about Octopus — a real supplier
  absent from `electricity_rates` — the model reported that "Octopus no es una empresa de servicios
  públicos disponible en el mercado español". The old `search_market_rates` empty result ("No se han
  encontrado tarifas de mercado para la compañía 'X'") reads as non-existence. It now denies the
  inference explicitly and lists the companies the catalogue *does* hold, so the model can neither
  conclude the company is fake nor claim ignorance of what it has. Grounding the correction in the
  tool result rather than the prompt matters: a rule can be ignored, a literal cannot.
- **Tool-capable model dependency.** With the flag on, `smartChatModel` must support tool calling;
  otherwise keep it off. Documented here and in `PLAN.md`.
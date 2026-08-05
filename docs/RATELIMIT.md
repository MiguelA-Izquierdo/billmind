# Rate Limiting

> **Status:** implemented and green (`_shared/infrastructure/ratelimit/`, Caffeine store,
> pre/post-auth checkpoints, 50+ tests). Closes security debt `A04 — No rate limiting on upload`.
> Outstanding: Redis store for global multi-instance limits (Milestone 9).

## Why per-endpoint

Endpoints differ wildly in cost and threat, so a single global limit is wrong:

| Profile | Routes | Key | Fail mode | Starting limit |
|---|---|---|---|---|
| `UPLOAD` | `POST /invoices` | session + IP ceiling | fail-closed | session 3/h · IP 30/h |
| `CHAT` | `POST /assistant/chat` | session + IP ceiling | fail-closed | session 20/min · IP 60/min |
| `ADMIN` | `/admin/**` except `GET` | IP + token hash | fail-closed | 5/min per layer |
| `ADMIN_READ` | `GET /admin/**` (rate listing, KB search) | IP + token hash | fail-closed | 30/min per layer |
| `PUBLIC_READ` | cheap `GET`s (invoices) | IP | fail-open | 60/min |
| `DEFAULT` | any unmapped `/api/v1/` | session + IP ceiling | fail-closed | session 30/min · IP 60/min |
| `NONE` | actuator, static | — | — | unlimited |

Numbers live in `application.properties` (`billmind.ratelimit.*`), nothing hardcoded. `RateLimitPolicy`
carries a `cost` field so an expensive upload draws more tokens than a cheap read from the same bucket.
The resolver reuses `RouteAccessPolicy` for the ADMIN/OPEN/ANONYMOUS split so it can't drift from the
auth filters, then splits the admin class by verb.

`ADMIN_READ` exists because 5/min was sized to guard destructive routes and only got in the honest
operator's way on a page they refresh by hand. The widening is not free and is taken deliberately: the
`IP` layer is the pre-auth cap, so an attacker spraying tokens gets the same 30/min headroom on admin
`GET`s. What that buys them is attempts against a high-entropy credential and reach over nothing that
mutates — the routes that change state keep the tight budget. Tune with `RATELIMIT_ADMIN_READ_*`.

## Build vs buy

Token-bucket algorithm + atomic store (incl. Redis Lua, clock skew) is delegated to **bucket4j**
(Apache-2.0, `com.bucket4j:bucket4j_jdk17-*`, package still `io.github.bucket4j`). Everything above the
store — filter, policy resolution, keys, cost, fail policy, Spanish `429`, metrics — is first-party.
A first-party `RateLimitStore` port keeps bucket4j out of the app so Caffeine→Redis is one adapter.

## Request flow

`RateLimitFilter` (before `JwtAuthFilter`) resolves the profile, derives the key, calls
`RateLimitStore.tryConsume(key, policy)`. Allowed → `X-RateLimit-*` headers + continue. Denied →
`429` with a Spanish body + `Retry-After`. For `ADMIN`, `PostAuthRateLimitFilter` runs a second layer
after auth, keyed by the validated identity in the `SecurityContext`. Filters write JSON directly
(they run before `DispatcherServlet`, so no `GlobalExceptionHandler`).

## The IP ceiling (the key design point)

`X-Session-Id` is client-generated and unauthenticated, so keying `UPLOAD`/`CHAT` by session alone was
a **bypass**: a fresh `UUID.randomUUID()` per request minted a new full bucket, and the paid LLM routes
were unlimited from one laptop. Same rotation attack `ADMIN` already defended by keying IP pre-auth.

Fix: `UPLOAD`/`CHAT`/`DEFAULT` now declare `[SESSION, IP]` — **two separate buckets**, not one:

- **L1 session** — one honest visitor's fair share; bites when a real user loops.
- **L2 IP ceiling** — volumes no human produces; bites when someone rotates sessions to escape L1.

Set an order of magnitude above L1 (uploading a bill is rare → 30/h leaves a whole CGNAT untouched
while capping a rotating attacker). `RateLimiter` evaluates layers in order, **first breach wins**, so
an honest user hits their own session limit before touching the shared IP bucket. Two buckets per
profile come from `billmind.ratelimit.profiles.<p>.overrides.<key-type>.*`.

The session share is **3 uploads** (`capacity` 15 ÷ `cost` 5), not one: a visitor arrives with the bill
that made them curious and then goes to fetch the two they wanted to compare it against, and a limit
that stops them mid-errand reads as breakage, not as protection. Refill is greedy, so the fourth
unblocks ~20 minutes later rather than on the hour. The ceiling moved with it, 10/h → 30/h, to keep
the order of magnitude the CGNAT argument above depends on: left at 10 it would fit barely three
honest visitors per shared egress and they would start throttling each other. The cost is real and
taken deliberately — that headroom is also what a session-rotating attacker gets — and it is bounded
by `cost=5` on the session layer, which keeps the paid LLM path from ever being the cheap one to
hammer.

Related fix: `SessionFilter` now writes a `sessions` row only on non-`GET` requests, so a forged header
on a read no longer causes an unauthenticated INSERT.

**Still open:** this converts "unlimited from one laptop" into "you need a botnet". A distributed
attacker with many IPs still slips under it — backstop is the global LLM cost circuit breaker
(Milestone 10), then CAPTCHA/accounts (Milestone 9). Also: the IP ceiling is only as good as the client
IP, so trust `X-Forwarded-For` (`billmind.ratelimit.trust-forwarded-for`) **only** behind a trusted proxy.

## Fail policy

On store failure the limiter can't count and must default. Paid/security profiles (`UPLOAD`, `CHAT`,
`ADMIN`) are **fail-closed** — deny, so LLM cost / brute-force defense survives an outage. Cheap
`PUBLIC_READ` is **fail-open** — availability wins. A fail-closed denial is **`503`** (with
`Retry-After`), reserving **`429`** for an actual breach; the two must not be conflated.

## The other throttle: the model provider

Our bucket is not the only thing that says "slow down". A cloud provider throttles by tokens or by
requests per minute, and on the free tiers (Groq especially) it bites long before our limits do. To
the person reading the screen these are the same event, so they share one vocabulary:

| | Source | Where it is decided | Body written by |
|---|---|---|---|
| Our allowance | `RateLimitFilter` / `PostAuthRateLimitFilter` | before `DispatcherServlet` | the filter, directly |
| The provider's | `LlmFailures` → `LlmRateLimitedException` | inside the request | `GlobalExceptionHandler` |

Both answer **`429`** with **`Retry-After`** and a Spanish message from `ThrottleMessages`, so a client
handles one case, not two. The provider's wait is scraped out of the error body — `"Please try again in
2m30.5s"`, or a `retry-after: 30` echoed from the header — and is `Duration.ZERO` when it named none,
which every caller must read as *unknown*, never as *retry now*. Classification lives in
`TimedChatLanguageModel`; see ARCHITECTURE Design Decision #15 for why it sits there and nowhere else.

Waits are rounded **up** and stated coarsely ("en 12 minutos"): understating a wait earns a second
`429`, and "en 11 minutos y 38 segundos" invites the user to sit and watch a clock.

`POST /assistant/chat` is the exception to the table, because SSE commits its status when the stream
opens: a throttle arrives there as an `error` event with `code: "RATE_LIMITED"` and a `retryAfter` in
seconds. Same information, different envelope.

## What the user sees

The rule is that **no interface path can print an HTTP status**. `static/chat/js/errors.js` is the one
place a failure becomes text — `describeFailure()` takes `{status, code, message, retryAfter}` from
either envelope and returns copy plus a `kind`. A `4xx` keeps the backend's own Spanish message (it
knows exactly what is wrong with the request); a `5xx` never does, since "error interno del servidor"
tells the user nothing they can act on.

A throttle additionally starts a **cooldown** (`static/chat/js/cooldown.js`): the deadline goes into
`state.cooldownUntil`, `syncChat()` reads it and locks the input and the upload button for exactly
that long, and a banner counts it down while the bot delivers the line by toast. Letting the button
stay live would only buy the user a second `429`. When the wait is unknown the message is shown but
**nothing is locked** — guessing a deadline is worse than not having one.

`Retry-After` and the `X-RateLimit-*` headers are listed in `SecurityConfig`'s `exposedHeaders`.
Without that a cross-origin UI (the Milestone 8 frontend) cannot read them and the countdown degrades
silently to a vague "try later" — same-origin callers are unaffected, which is exactly what makes the
omission easy to miss.

## Observability

Following `MetricsLlmTelemetry` (Micrometer, bounded cardinality — the key is **never** a tag):
`ratelimit.requests{profile,phase,outcome=allowed|throttled|unavailable}` and
`ratelimit.store.errors{profile}`. Throttles log at `WARN` with a truncated SHA-256 fingerprint of the
key (never IPs/sessions/tokens in clear — security rule #6).

## Store migration — Caffeine → Redis

Today: Caffeine `ProxyManager`, per-instance (correct for single-instance). Later: add Lettuce + a
Redis `ProxyManager` bean, flip `billmind.ratelimit.store=redis` — one property + one bean, no data
migration (counters are ephemeral/TTL-bound). Redis makes the limit global across instances, so
re-tune the numbers (roughly ÷ instance count).

## Filter chain order (fragile — pinned by a test)

`SecurityConfig` wires `RateLimitFilter` → `JwtAuthFilter` → `PostAuthRateLimitFilter` → `SessionFilter`,
all ahead of Spring's `AuthorizationFilter`. Custom filters anchored on the same built-in land on the
same slot (step of 1, not 100) and the tie is resolved only by insertion order — an invisible
dependency that once silently reversed the chain. It is now a single descending `addFilterBefore` chain
so each filter gets its own slot, and `SecurityFilterChainOrderTest` asserts the order.
# Rate Limiting

> **Status:** implemented and green (`_shared/infrastructure/ratelimit/`, Caffeine store,
> pre/post-auth checkpoints, 50+ tests). Closes security debt `A04 — No rate limiting on upload`.
> Outstanding: Redis store for global multi-instance limits (Milestone 9).

## Why per-endpoint

Endpoints differ wildly in cost and threat, so a single global limit is wrong:

| Profile | Routes | Key | Fail mode | Starting limit |
|---|---|---|---|---|
| `UPLOAD` | `POST /invoices` | session + IP ceiling | fail-closed | session ~1/h · IP 10/h |
| `CHAT` | `POST /assistant/chat` | session + IP ceiling | fail-closed | session 20/min · IP 60/min |
| `ADMIN` | `/admin/**`, `DELETE /market-rates` | IP + token hash | fail-closed | 5/min per layer |
| `PUBLIC_READ` | cheap `GET`s (market-rates, invoices) | IP | fail-open | 60/min |
| `DEFAULT` | any unmapped `/api/v1/` | session + IP ceiling | fail-closed | session 30/min · IP 60/min |
| `NONE` | actuator, static | — | — | unlimited |

Numbers live in `application.properties` (`billmind.ratelimit.*`), nothing hardcoded. `RateLimitPolicy`
carries a `cost` field so an expensive upload draws more tokens than a cheap read from the same bucket.
The resolver reuses `RouteAccessPolicy` for the ADMIN/OPEN/ANONYMOUS split so it can't drift from the
auth filters.

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

Set an order of magnitude above L1 (uploading a bill is rare → 10/h leaves a whole CGNAT untouched
while capping a rotating attacker). `RateLimiter` evaluates layers in order, **first breach wins**, so
an honest user hits their own session limit before touching the shared IP bucket. Two buckets per
profile come from `billmind.ratelimit.profiles.<p>.overrides.<key-type>.*`.

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
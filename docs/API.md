# BillMind — API Reference

Base URL: `http://localhost:8082` (configurable via `SERVER_PORT`)

---

## Response envelope

All responses follow a consistent envelope:

```json
// Success
{ "success": true,  "status": 200, "message": "...", "data": { ... } }

// Error (with field-level detail)
{ "success": false, "status": 400, "message": "...", "errors": { "field": { "code": "message" } } }

// Error (no field detail)
{ "success": false, "status": 404, "message": "..." }
```

`data` is omitted when `null`. `errors` is omitted when there are no field-level details.

User-facing `message` strings are in Spanish.

---

## Authentication

BillMind never validates tokens itself — all authentication is delegated to an external user microservice (`AUTH_EXTERNAL_URL`).

### Anonymous endpoints (Phase 1)

Every request must include an `X-Session-Id` header with a client-generated UUID. The backend correlates resources (invoices, conversations) to that UUID without authenticating the caller.

| Header | Example | Description |
|---|---|---|
| `X-Session-Id` | `550e8400-e29b-41d4-a716-446655440000` | Client-generated UUID identifying the visitor session |

Missing or malformed `X-Session-Id` returns `400 Bad Request`.

### Admin endpoints

Admin operations require a Bearer token issued by the external user microservice.

| Header | Example | Description |
|---|---|---|
| `Authorization` | `Bearer eyJ…` | JWT issued by the external auth service |

Authenticating and authorizing are separate steps. `JwtAuthFilter` only establishes an identity: it calls `GET <AUTH_EXTERNAL_URL>/introspect` forwarding the Bearer token and puts the result in the `SecurityContext` (`ROLE_ADMIN` only on a `200`) — it rejects nothing. The access **decision** is taken by Spring Security's authorization engine and enforced again by `@PreAuthorize` on each admin handler. From the caller's side the observable contract is unchanged: a valid token proceeds; a missing token yields `401`; a token rejected by introspection (or any connectivity error, which fails closed) yields `403`. Admin routes do **not** require `X-Session-Id`. See [`ARCHITECTURE.md`](ARCHITECTURE.md) → *Admin route protection* for what each layer catches.

---

## Rate limiting

Every `/api/v1/**` route is rate-limited by a per-endpoint token-bucket limiter (see [`RATELIMIT.md`](RATELIMIT.md)). Three distinct status codes mean "you were denied, not served":

| Status | Meaning | When |
|---|---|---|
| `429 Too Many Requests` | An actual limit was breached. | The caller exceeded the bucket for the route's profile (session and/or IP layer) — **or** the model provider throttled us on a route that calls an LLM. |
| `503 Service Unavailable` | The limiter could not count and failed **closed**. | The rate-limit store is unavailable on a paid/security profile (`UPLOAD`, `CHAT`, `ADMIN`); denying is safer than serving unmetered. |
| `503 Service Unavailable` | The model provider is down, timing out, or refusing our credentials. | Raised from inside the handler, so `X-RateLimit-*` may be present on it. |

All carry a Spanish message in the standard error envelope. `429` carries `Retry-After` (seconds) whenever the wait is known — our own bucket always knows it, a provider only sometimes says. Responses that consulted a bucket also expose `X-RateLimit-Limit`, `X-RateLimit-Remaining` and `X-RateLimit-Reset` (seconds from now).

```json
{ "success": false, "status": 429, "message": "Has agotado tus consultas por ahora. Vuelve a intentarlo en 12 minutos." }
{ "success": false, "status": 429, "message": "Estamos atendiendo muchas consultas ahora mismo. Vuelve a intentarlo en 2 minutos." }
{ "success": false, "status": 503, "message": "El servicio no está disponible temporalmente. Inténtalo de nuevo en unos instantes." }
```

The closing sentence degrades to "Inténtalo de nuevo más tarde." when the wait is unknown. A client should read the `Retry-After` header rather than parse it out of the text.

---

## Endpoints

### POST /api/v1/invoices

Upload a utility invoice PDF. The API validates the file, extracts text, classifies the document (supply type + provider company), extracts structured fields (billing period, consumption, rates, totals), redacts PII, and persists the invoice to the database.

**Request**

```
Content-Type: multipart/form-data
```

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | PDF file | Yes | Utility invoice (electricity, gas, water, telecoms) |

**Responses**

`201 Created` — invoice accepted and persisted. The `comparison` field carries the savings breakdown computed synchronously on upload; it is `null` when the invoice has no extracted fields, or when there is insufficient data to compare (missing `pricePerKwh`/`consumptionKwh`, or no market rates loaded yet):
```json
{
  "success": true,
  "status": 201,
  "message": "Factura subida y procesada correctamente.",
  "data": {
    "invoiceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "factura_luz.pdf",
    "comparison": {
      "userPricePerKwh": 0.1542,
      "userIsTou": false,
      "annualKwhEstimate": 3600,
      "userAnnualCostEuros": 748.30,
      "invoiceTotalEuros": 135.64,
      "basis": {
        "observedDays": 32,
        "annualised": true,
        "powerTerm": "READ",
        "consumptionProfile": "ACTUAL",
        "taxesIncluded": true
      },
      "flatBlock": {
        "bestCompany": "PODO",
        "bestTariffName": "Tarifa Estable",
        "bestPricePerKwh": 0.1190,
        "bestAnnualCostEuros": 621.58,
        "periodSavingsEuros": 38.27,
        "annualSavingsLow": 110.00,
        "annualSavingsHigh": 210.00,
        "annualSavingsMid": 160.00,
        "alternatives": [
          { "company": "PODO", "tariffName": "Tarifa Estable", "effectivePricePerKwh": 0.1190,
            "annualCostEuros": 621.58, "touRate": false }
        ]
      },
      "touBlock": null
    }
  }
}
```

The same comparison payload is available on demand at `GET /api/v1/invoices/{id}/comparison`.

**Reading the savings.** Offers are ranked on **total annual cost** — energy plus the power term (`contractedPowerKw × €/kW/día × 365`) — not on `€/kWh`. The power term is a fixed cost that does not scale with consumption, so an offer with cheap energy and an expensive standing charge can and does lose. `bestAnnualCostEuros` and each alternative's `annualCostEuros` are the figures the ranking actually used.

**Two blocks, and both are always offered.** `flatBlock` and `touBlock` hold the best offer of their kind; a block is `null` only when the corpus holds no offer of that kind. The user's own tariff shape does not suppress either one — a user already billed by periods gets `touBlock` too, since it names a concrete tariff rather than a kind of tariff, and moving from one period tariff to a cheaper one asks for no change of habits. `userIsTou` tells a client which scenario to lead with, not which to hide.

**Two savings, and they are not the same kind of number.** `periodSavingsEuros` is what the invoice in hand would have cost on the winning tariff: its own consumption, its own days, nothing extrapolated. It keeps its cents, carries no band, and pairs with `invoiceTotalEuros` so a client can show the bill the user is holding next to what it would have been. It is the only figure on the payload the user can verify, and clients should lead with it.

The annual saving is a **band**, never a point: it projects that billing period over a year the user has not lived. `annualSavingsLow`/`High` are rounded outwards to whole tens; `annualSavingsMid` is offered only so a client has one figure to headline. Both ends already carry IEE and IVA, as does `periodSavingsEuros`.

`basis` says what the band rests on, and a client that prints a figure must be able to print its caveats from the same payload:

| Field | Meaning |
|---|---|
| `observedDays` | Days the invoice covers |
| `annualised` | `true` when the year was extrapolated from a shorter period |
| `powerTerm` | `READ` off the invoice · `DERIVED` from its printed total · `UNAVAILABLE`, energy compared alone |
| `consumptionProfile` | `ACTUAL` per-period consumption, or an `ASSUMED` standard domestic profile |
| `taxesIncluded` | `false` only when the extracted prices already carried tax |

`comparison` is also `null` when the extracted fields **fail to reconcile** with the invoice's printed total: the parts not adding up means some extracted number is wrong, and no saving is quoted off it.

`400 Bad Request` — file is not a PDF (validation fails in `UploadInvoiceCommand`):
```json
{
  "success": false,
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "file": {
      "invalidFormat": "El archivo debe ser un PDF válido"
    }
  }
}
```

`422 Unprocessable Entity` — document is not a recognised supply invoice:
```json
{
  "success": false,
  "status": 422,
  "message": "El archivo no parece ser una factura de suministro del hogar (electricidad, gas, agua o telecomunicaciones)"
}
```

`429` / `503` — the upload calls the LLM twice (classification and field extraction), so besides the
limiter it can also return the provider's own throttle or outage. See [Rate limiting](#rate-limiting).

`500 Internal Server Error` — unexpected failure. Reserved for genuinely unforeseen faults: a
provider being throttled, down or unreachable is classified before it gets here (Design Decision #15),
and the body is always this fixed string — the underlying failure is kept as the exception cause, for
the logs only.
```json
{
  "success": false,
  "status": 500,
  "message": "Se ha producido un error interno en el servidor"
}
```

**Example**

```bash
curl -X POST http://localhost:8082/api/v1/invoices \
  -H "X-Session-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -F "file=@factura_luz.pdf"
```

---

### GET /api/v1/invoices

Returns all invoices uploaded in the current session.

**Responses**

`200 OK`:
```json
{
  "success": true,
  "status": 200,
  "message": "Facturas obtenidas correctamente",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "fileName": "factura_luz.pdf",
      "supplyType": "ELECTRICITY",
      "provider": "IBERDROLA",
      "uploadedAt": "2025-05-14T10:23:00Z"
    }
  ]
}
```

`supplyType` values: `ELECTRICITY`, `GAS`, `WATER`, `TELECOM`, `OTHER`.

**Example**

```bash
curl http://localhost:8082/api/v1/invoices \
  -H "X-Session-Id: 550e8400-e29b-41d4-a716-446655440000"
```

---

### GET /api/v1/invoices/{id}

Returns a single invoice. Only responds if the `X-Session-Id` header matches the session that uploaded the invoice.

**Path parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID | Invoice ID returned by the upload endpoint |

**Responses**

`200 OK`:
```json
{
  "success": true,
  "status": 200,
  "message": "Factura obtenida correctamente",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "fileName": "factura_luz.pdf",
    "supplyType": "ELECTRICITY",
    "provider": "IBERDROLA",
    "uploadedAt": "2025-05-14T10:23:00Z"
  }
}
```

`404 Not Found` — invoice does not exist or belongs to a different session:
```json
{
  "success": false,
  "status": 404,
  "message": "Factura no encontrada."
}
```

**Example**

```bash
curl http://localhost:8082/api/v1/invoices/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "X-Session-Id: 550e8400-e29b-41d4-a716-446655440000"
```

---

### GET /api/v1/admin/market-rates

Returns every stored market rate. **Admin endpoint** — requires a valid Bearer token. Reading the rate
corpus is guarded exactly like emptying it; see [`MARKET.md`](MARKET.md) for the response shape.

**Headers**

| Header | Required | Description |
|---|---|---|
| `Authorization` | Yes | `Bearer <token>` issued by the external auth service |

**Responses** — `200 OK` with the rate list; `401` / `403` exactly as the `DELETE` below.

**Example**

```bash
curl http://localhost:8082/api/v1/admin/market-rates \
  -H "Authorization: Bearer eyJ…"
```

---

### DELETE /api/v1/admin/market-rates

Deletes all electricity rate records from the database. **Admin endpoint** — requires a valid Bearer token.

**Headers**

| Header | Required | Description |
|---|---|---|
| `Authorization` | Yes | `Bearer <token>` issued by the external auth service |

**Responses**

`200 OK`:
```json
{ "success": true, "status": 200, "message": "Todas las tarifas de mercado han sido eliminadas" }
```

`401 Unauthorized` — missing or absent `Authorization` header:
```json
{ "success": false, "status": 401, "message": "Se requiere autenticación para realizar esta operación" }
```

`403 Forbidden` — token present but rejected by the external auth service:
```json
{ "success": false, "status": 403, "message": "No tienes permisos para realizar esta operación" }
```

**Example**

```bash
curl -X DELETE http://localhost:8082/api/v1/admin/market-rates \
  -H "Authorization: Bearer eyJ…"
```

---

## Health

Actuator is served on a separate, internal-only management port (`8083` by default,
override with `MANAGEMENT_PORT`), **not** on the application port. It is intentionally
not published by Docker so it stays unreachable from the host / public network — point
Docker / Kubernetes liveness & readiness probes at it over the internal network.

```
GET http://localhost:8083/actuator/health
```

`show-details` is `when-authorized`: an unauthenticated caller only sees the status,
while the DB / Kafka / disk breakdown and liveness/readiness probes require authorization.

```json
{ "status": "UP" }
```

### GET /ping

The one health signal on the **public** application port, for uptime monitors and load balancers
that cannot reach the management port. No `X-Session-Id`, no token, no CORS restriction.

```bash
curl -i http://localhost:8082/ping
```

| Status | Meaning |
|---|---|
| `200 OK` | PostgreSQL — and Kafka, when `KAFKA_ENABLED=true` — answered. |
| `503 Service Unavailable` | One of them did not. |

The body is **always empty**, in both directions: the caller learns whether the service can work
and nothing more. Which dependency failed, and why, stays on the authenticated Actuator endpoint
above. The answer carries `Cache-Control: no-store` so no proxy keeps serving a stale `200`.

It sits outside `/api/v1/**` deliberately. Inside that tree every unrecognized route is classified
as anonymous-with-session, which would make `X-Session-Id` mandatory — a header no monitor sends.
Being outside it also means the rate limiter does not meter it; instead `DependencyHealthProbe`
caches its verdict for 5 seconds, so hammering `/ping` cannot translate into one database
connection plus one Kafka `AdminClient` per request.

The database check is capped at 2 seconds. A dead Postgres blocks in `getConnection()` for the
pool's `connection-timeout` (30s by default), which the validation query never bounds — long
enough for a monitor to time out on a probe that already knows the answer. The probe runs on its
own thread and gives up at the cap, so the caller always gets the `503` rather than a hung socket.
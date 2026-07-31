# BillMind — Market Module

The `market` bounded context ingests current electricity tariff rates from an external service via Kafka and persists them in PostgreSQL. These rates are later used by the comparison engine to benchmark a user's invoice against the current market.

---

## How it works

An external service publishes `ElectricityPriceUpdated` events to a Kafka topic. BillMind consumes those events, validates the domain rules, and upserts the rate into the `electricity_rates` table. Failed events are retried automatically and eventually routed to a dead-letter topic.

```
External service → Kafka (market.electricity-price-updated) → ElectricityPriceConsumer → PostgreSQL
```

Kafka must be enabled via `KAFKA_ENABLED=true` in your `.env` (required when using `--profile kafka`).

---

## Fallback offers when the corpus is empty

Without a Kafka producer, `electricity_rates` stays empty and the comparison engine has nothing to
benchmark against — so a freshly cloned repo would upload an invoice and get no savings figure. To
keep the flow demonstrable, `MarketOfferQueryAdapter` reads six example 2.0TD offers from
`src/main/resources/comparison/fallback-electricity-offers.json` (once, at startup) and serves them
**only while the rate corpus is empty**.

Deliberately a read path, not a seed:

* **Nothing is persisted.** No rows to clean up, and `GET /api/v1/admin/market-rates` keeps
  reporting the real corpus — empty is reported as empty.
* **Real rates win automatically.** The first rate arriving via Kafka makes the fallback disappear;
  there is no precedence rule to reason about and no mixed corpus.
* **Three flat-price + three time-of-use offers**, because the comparison builds those two blocks
  independently and each needs a winner plus alternatives.
* **The prices are plausible but invented, and the company names are generic**
  (`Comercializadora Ejemplo Uno`, …) so no offer is ever attributed to a real retailer. They are
  example data for a working demo, not market intelligence.

Set `COMPARISON_FALLBACK_OFFERS_ENABLED=false` to turn the fallback off and have an empty corpus
report no alternatives, as it did before this existed.

---

## Kafka event

**Topic:** `market.electricity-price-updated`

Events must be JSON with a `type` discriminator field:

```json
{
  "type": "ElectricityPriceUpdated",
  "eventId": "1f1dcbbe-1f5d-4e06-bf27-5a44db91ef2a",
  "company": "Naturgy",
  "tariffName": "Tarifa Por Uso Luz",
  "pricePerKwh": 0.18500,
  "pricePerKwhValle": null,
  "pricePerKwhLlano": null,
  "pricePerKwhPunta": null,
  "contractedPowerPrice": 0.10200,
  "contractedPowerPriceP2": null,
  "validFrom": "2026-05-01",
  "validTo": null,
  "region": "ES",
  "source": "https://...",
  "publishedAt": "2026-05-21T18:00:00Z"
}
```

**Required fields:** `eventId`, `company`, `tariffName`, `validFrom`, `source`, and at least one of `pricePerKwh` / `pricePerKwhValle`.

**Flat-rate tariffs** set `pricePerKwh` and leave the time-of-use fields (`Valle`, `Llano`, `Punta`) as `null`. **Time-of-use tariffs** set those three fields and may leave `pricePerKwh` as `null`.

---

## Error handling

| Failure | Behaviour |
|---|---|
| Deserialization error / unknown event type | Sent directly to DLT (no retries) |
| Domain validation failure (negative price, missing field) | Published to `market.electricity-price-updated.domain-errors` |
| Transient infrastructure error | Retried up to 3 times with exponential backoff (1 s × 2), then sent to DLT |

**Dead-letter topic:** `market.electricity-price-updated.DLT`

---

## REST API

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/market-rates` | **Admin** — `Authorization: Bearer` | Returns all stored market rates |
| `DELETE` | `/api/v1/admin/market-rates` | **Admin** — `Authorization: Bearer` | Deletes all stored market rates |

Both verbs are guarded identically: the rate corpus is the market intelligence the product compares invoices against, so reading it is an admin operation too. They live under `/api/v1/admin/` precisely so that the guard is the path — `RouteAccessPolicy` classifies the whole tree as `ADMIN`, `JwtAuthFilter` introspects the token against the external auth service, and `@PreAuthorize("hasRole('ADMIN')")` on each handler is the third layer. See [`API.md`](API.md) and [`ARCHITECTURE.md`](ARCHITECTURE.md) → *Admin route protection*.

### `GET /api/v1/admin/market-rates` — response

```json
{
  "success": true,
  "status": 200,
  "message": "Tarifas de mercado obtenidas correctamente",
  "data": [
    {
      "id": "1f1dcbbe-1f5d-4e06-bf27-5a44db91ef2a",
      "supplyType": "ELECTRICITY",
      "company": "Naturgy",
      "tariffName": "Tarifa Por Uso Luz",
      "pricePerKwh": 0.18500,
      "pricePerKwhValle": null,
      "pricePerKwhLlano": null,
      "pricePerKwhPunta": null,
      "contractedPowerPrice": 0.10200,
      "contractedPowerPriceP2": null,
      "validFrom": "2026-05-01",
      "validTo": null,
      "region": "ES",
      "source": "https://...",
      "receivedAt": "2026-05-21T18:16:03Z"
    }
  ]
}
```

---

## Static viewer

A simple HTML page is served at `/market-rates.html` to inspect stored rates without needing a REST client. It fetches `GET /api/v1/admin/market-rates` and renders the results in a table.

The page itself is served to anyone — a browser navigation carries no `Authorization` header, so with a stateless bearer model there is nothing to validate at that point. What it is *not* is a way in: the page ships empty and every row it shows comes from the guarded endpoint above. Without a token the server has validated, it renders a locked state and asks for one, which it keeps in `sessionStorage` for the tab's lifetime and sends on both the read and the delete. Protecting the file itself would take a session cookie and a login screen — Milestone 9.
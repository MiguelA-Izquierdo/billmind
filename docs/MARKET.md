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

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/market-rates` | Returns all stored market rates |
| `DELETE` | `/api/v1/market-rates` | Deletes all stored market rates _(development only — will be removed)_ |

### `GET /api/v1/market-rates` — response

```json
{
  "success": true,
  "status": 200,
  "message": "Tarifas de mercado obtenidas correctamente",
  "data": [
    {
      "id": "1f1dcbbe-1f5d-4e06-bf27-5a44db91ef2a",
      "supplyType": "LUZ",
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

A simple HTML page is served at `/market-rates.html` to inspect stored rates without needing a REST client. It fetches `GET /api/v1/market-rates` and renders the results in a table.
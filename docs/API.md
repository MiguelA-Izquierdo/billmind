# BillMind — API Reference

Base URL: `http://localhost:8082` (configurable via `SERVER_PORT`)

---

## Response envelope

All responses follow a consistent envelope:

```json
{ "status": "success", "data": { ... } }
{ "status": "error",   "message": "...", "errors": { "field": "message" } }
```

User-facing `message` strings are in Spanish.

---

## Endpoints

### POST /api/v1/invoices/upload

Upload a utility invoice PDF. The API classifies it, splits it into semantic chunks, generates embeddings, and persists everything to the vector store.

**Request**

```
Content-Type: multipart/form-data
```

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | PDF file | Yes | Utility invoice (electricity, gas, water, telecoms) |

**Headers**

| Header | Example | Description |
|---|---|---|
| `X-Session-Id` | `550e8400-e29b-41d4-a716-446655440000` | Client-generated UUID. Correlates uploads to a visitor session (no auth required in Phase 1). |

**Responses**

`201 Created` — invoice accepted and vectorized:
```json
{
  "status": "success",
  "data": {
    "invoiceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "factura_luz.pdf"
  }
}
```

`415 Unsupported Media Type` — file is not a PDF:
```json
{
  "status": "error",
  "message": "El archivo debe ser un PDF válido."
}
```

`422 Unprocessable Entity` — document is not a recognised supply invoice:
```json
{
  "status": "error",
  "message": "El documento no es una factura de suministro reconocida."
}
```

`500 Internal Server Error` — unexpected failure:
```json
{
  "status": "error",
  "message": "Ha ocurrido un error interno. Por favor, inténtalo de nuevo."
}
```

**Example**

```bash
curl -X POST http://localhost:8082/api/v1/invoices/upload \
  -H "X-Session-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -F "file=@factura_luz.pdf"
```

---

## Health

```
GET /actuator/health
```

Returns Spring Boot Actuator health status. Useful for Docker / Kubernetes liveness probes.

```json
{ "status": "UP" }
```
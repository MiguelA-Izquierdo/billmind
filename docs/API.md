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

On each admin request BillMind calls `GET <AUTH_EXTERNAL_URL>/introspect` forwarding the Bearer token. A `200` response authorises the request; `401`/`403` or any connectivity error returns `401`/`403` to the caller respectively. Admin routes do **not** require `X-Session-Id`.

---

## Endpoints

### POST /api/v1/invoices/upload

Upload a utility invoice PDF. The API validates the file, extracts text, classifies the document (supply type + provider company), extracts structured fields (billing period, consumption, rates, totals), redacts PII, and persists the invoice to the database.

**Request**

```
Content-Type: multipart/form-data
```

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | PDF file | Yes | Utility invoice (electricity, gas, water, telecoms) |

**Responses**

`201 Created` — invoice accepted and persisted:
```json
{
  "success": true,
  "status": 201,
  "message": "Factura subida y procesada correctamente.",
  "data": {
    "invoiceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "factura_luz.pdf"
  }
}
```

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

`500 Internal Server Error` — unexpected failure:
```json
{
  "success": false,
  "status": 500,
  "message": "Se ha producido un error interno en el servidor"
}
```

**Example**

```bash
curl -X POST http://localhost:8082/api/v1/invoices/upload \
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
      "supplyType": "LUZ",
      "provider": "IBERDROLA",
      "uploadedAt": "2025-05-14T10:23:00Z"
    }
  ]
}
```

`supplyType` values: `LUZ`, `GAS`, `AGUA`, `TELCO`, `OTRO`.

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
    "supplyType": "LUZ",
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

### DELETE /api/v1/market-rates

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
curl -X DELETE http://localhost:8082/api/v1/market-rates \
  -H "Authorization: Bearer eyJ…"
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
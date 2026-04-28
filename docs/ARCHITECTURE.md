# Arquitectura y Decisiones de Diseño — BillMind

Referencia para sesiones donde se diseñen o extiendan módulos. Usar con `@docs/ARCHITECTURE.md`.

---

## Decisiones de Diseño

1. **UUID generado en el controller** — CQRS estricto: los commands no devuelven valores (`CommandBus.dispatch()` retorna `void`)
2. **AllMiniLM-L6-v2** local via Ollama — sin API externa, 384 dimensiones, rápido
3. **HNSW** como índice de pgVector — buen balance velocidad/precisión
4. **Chunk size 500, overlap 100** — configurable si se identifican mejores parámetros
5. **Ollama** completamente local — facturas son datos sensibles, sin envío a APIs externas
6. **Domain Events** implementados pero sin listeners activos — infraestructura lista para el futuro

---

## Añadir un nuevo módulo (ej: `comparison/`)

1. Crear estructura: `domain/model/`, `domain/port/`, `application/usecase/`, `infrastructure/`
2. El dominio no importa nada de Spring o LangChain4j
3. Los beans de Spring van en `infrastructure/config/`
4. Seguir el patrón exacto de `invoice/`

## Añadir búsqueda semántica (RAG)

- Port en `invoice/domain/port/InvoiceSearchRepository.java`
- Implementación en `invoice/infrastructure/adapter/PgVectorSearchRepository.java`
- Usar `EmbeddingStoreRetriever` de LangChain4j
- Prompt del LLM en `infrastructure/ai/` (nunca en dominio)

## Añadir autenticación JWT

- Filtro de Spring Security en `_shared/infrastructure/security/`
- `JWT_SECRET` y `JWT_EXPIRATION` se leen desde `application.properties` con `@Value`
- `SecurityFilterChain` bean configura los endpoints protegidos

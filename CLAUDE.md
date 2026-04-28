# CLAUDE.md — BillMind

Instrucciones de comportamiento para Claude Code. Para decisiones de arquitectura y puntos de extensión: `@docs/ARCHITECTURE.md`.

---

## Descripción del Proyecto

**BillMind** es una API REST en Spring Boot 3.2.5 + Java 21 + LangChain4j 0.33.0 que recibe facturas PDF, las fragmenta en chunks semánticos, genera embeddings con AllMiniLM-L6-v2 (local via Ollama) y los almacena en PostgreSQL 16 + pgVector (HNSW, 384 dim).

**Estado actual:** módulo `invoice/` funcional. Módulos `comparison/` y `market/` son scaffolding futuro.

---

## Arquitectura

Hexagonal Architecture (Ports & Adapters) + DDD:

```
src/main/java/com/demo/billmind/
├── _shared/                          # Cross-cutting concerns
│   ├── application/service/          # PropertyExtractorService
│   ├── domain/
│   │   ├── event/                    # DomainEvent, BaseDomainEvent, DomainEventPublisher
│   │   ├── exceptions/               # ValidationErrorsException
│   │   └── model/                    # PaginatedResult<T>
│   └── infrastructure/
│       ├── GlobalExceptionHandler    # @ControllerAdvice centralizado
│       ├── dto/                      # ErrorResponseDTO, SuccessResponseDTO
│       └── event/                    # SpringDomainEventPublisher
│
├── invoice/                          # Bounded Context: Gestión de Facturas
│   ├── domain/
│   │   ├── model/                    # Invoice, InvoiceChunk, InvoiceReference
│   │   └── port/                     # InvoiceParser, InvoiceVectorRepository
│   ├── application/usecase/          # UploadInvoiceUseCase
│   └── infrastructure/
│       ├── adapter/                  # PdfInvoiceParser, PgVectorInvoiceRepository
│       ├── config/                   # LangChain4jConfig, ApplicationUseCaseConfig
│       └── controller/               # InvoiceController + dto/InvoiceUploadResponse
│
├── comparison/                       # Módulo futuro
└── market/                           # Módulo futuro
```

### Reglas (NUNCA violar)

```
Infrastructure → Application → Domain
```

El paquete `domain/` no puede importar: Spring, JPA, LangChain4j, Lombok, Jackson. Solo `java.*`.

---

## Convenciones del Código

**Idioma:** código en inglés, comentarios/Javadoc en español, mensajes de error al usuario en español.

**Estilo:**
- Indentación: 4 espacios — Clases: `PascalCase` — Métodos/variables: `camelCase` — Constantes: `UPPER_SNAKE_CASE`
- Métodos máximo 20 líneas
- DTOs como `record` de Java 21
- Constructor injection obligatoria (nunca `@Autowired` en campos)

**Errores:**
- Excepciones de dominio propias (nunca `RuntimeException` genérica)
- Logging solo en capa de infraestructura
- `GlobalExceptionHandler` centraliza todas las respuestas de error

**Git Commits:**
```
feat|fix|refactor|test|security|docs(scope): descripción breve
```
Scopes: `invoice`, `comparison`, `market`, `shared`, `config`, `api`, `architecture`

---

## Tests

| Capa | Tipo | Anotaciones |
|---|---|---|
| `domain/` | Unit test | Solo `@Test` |
| `application/usecase/` | Unit test con Mockito | `@ExtendWith(MockitoExtension.class)` |
| `infrastructure/adapter/` | Integration test | `@SpringBootTest` + TestContainers |
| `infrastructure/controller/` | Integration test | `@SpringBootTest` + `@AutoConfigureMockMvc` |

- Naming: `should[Estado]When[Condición]()` o `given[Ctx]_when[Acción]_then[Resultado]()`
- Tests de integración con sufijo `*IT.java`
- Nunca mockear la clase bajo test
- Siempre probar happy path + casos nulos + edge cases

---

## Seguridad

1. Nunca hardcodear credenciales — siempre `@Value("${propiedad}")`
2. Validar archivos PDF subidos: MIME type real, tamaño máximo, nombre sanitizado
3. Nunca concatenar input de usuario en queries SQL — usar JPA/parámetros preparados
4. Nunca concatenar input de usuario en system prompts de LLM (Prompt Injection)
5. CORS nunca usar `*` en producción con endpoints autenticados
6. Logs no deben contener contenido de facturas, tokens JWT ni credenciales

---

## Respuestas de la API

```json
{ "status": "success", "data": { ... } }
{ "status": "error", "message": "...", "errors": { "campo": "mensaje" } }
```

---

## Agentes Disponibles (`.agents/`)

| Agente | Archivo | Cuándo invocarlo |
|---|---|---|
| Arquitecto | `architect.md` | Diseñar nuevas funcionalidades o módulos DDD |
| Desarrollador | `developer.md` | Implementar Use Cases, Adapters, Controllers |
| Experto de Dominio | `domain-expert.md` | Validar naming, lenguaje ubicuo, reglas de negocio |
| Tester | `tester.md` | Escribir tests o validar cobertura |
| Seguridad | `security.md` | Auditar código nuevo, endpoints, manejo de archivos |

**Flujo para nuevas features:** Arquitecto → Experto de Dominio → Desarrollador → Tester → Seguridad

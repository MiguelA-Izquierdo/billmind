# BillMind — Test Guide

---

## Running tests

```bash
# Unit tests only — no Docker required, runs in seconds
./mvnw test

# Unit + integration tests — requires Docker (TestContainers spins up PostgreSQL)
./mvnw verify

# Coverage report — generated after verify
./mvnw verify
# → open target/site/jacoco/index.html
```

---

## Test strategy

| Layer | Type | Annotations | Docker needed |
|---|---|---|---|
| `domain/` | Pure unit test | `@Test` only | No |
| `application/usecase/` | Unit test with Mockito | `@ExtendWith(MockitoExtension.class)` | No |
| `infrastructure/adapter/` | Unit test with Mockito | `@ExtendWith(MockitoExtension.class)` | No |
| `infrastructure/adapter/` (`*IT.java`) | Integration test | `@SpringBootTest` + TestContainers | Yes |
| `infrastructure/controller/` | MVC slice test | `@WebMvcTest` + `@MockBean` | No |

Integration tests are suffixed `*IT.java` and excluded from `mvn test` by default (bound to the `verify` phase).

---

## Naming conventions

```java
// State-based naming
shouldRejectWhenFileIsNotPdf()
shouldReturnInvoiceIdWhenValidPdfUploaded()

// Given/When/Then naming
givenBlankText_whenClassify_thenReturnsOTRO()
```

---

## What to cover

Every test must include at minimum:

- Happy path
- Null / empty input
- Relevant edge cases (e.g. PDF with no extractable text, unknown supply type)

Never mock the class under test. Domain tests must stay pure — no Spring context, no Mockito.

---

## Coverage

JaCoCo generates a coverage report on every `./mvnw verify` run. Open `target/site/jacoco/index.html` to inspect coverage by package. Enforcement (minimum threshold that fails the build) is not yet configured in `pom.xml` — add a `<check>` goal with `<rules>` to the JaCoCo plugin when a coverage baseline is established.
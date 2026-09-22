---
paths:
  - "api/**/*.java"
  - "api/pom.xml"
---
# Java API rules

- Java 21. Use records for DTOs, sealed types or enums for closed sets, and `var` only when the type is obvious.
- Constructor injection only. Wire dependencies by hand in `ApiHandler` (no DI framework, no static singletons except the cached `DataSource`).
- Layers: Controller → Service → Repository. Controllers hold no business logic, and repositories hold no rules.
- Status transitions live only in `JobPostingService` and follow the spec's lifecycle table exactly.
- Validate every request DTO with jakarta.validation annotations. Put cross-field checks (such as experienceMin ≤ experienceMax) in a class-level constraint.
- JDBC: prepared statements only. Never concatenate or format SQL with user input, including sort fields (map them to a whitelist).
- Updates use optimistic locking: `WHERE id = ? AND version = ?`. Zero rows updated → `409 version-conflict`.
- Errors: return RFC 7807 `application/problem+json` through one exception mapper. Never leak stack traces.
- Initialise the `DataSource`, Secrets Manager client, and `ObjectMapper` once per container, outside the handler method.
- Logging: one JSON line per request with `requestId`, method, path, status, and durationMs. Never log request bodies, secrets, or personal data.
- Every public service method has JUnit 5 tests: at least one **positive** (valid input, expected result) and the **negative** ones that apply (invalid field and its boundaries, forbidden status transition, not found, stale version, malformed input). Repositories have Testcontainers `*IT` tests against PostgreSQL 17.
- Name tests after what they prove, and after the acceptance criterion where there is one.
- No new dependency without saying why. Keep the shaded JAR under 20 MB.

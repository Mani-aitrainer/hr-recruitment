---
name: java-lambda-endpoint
description: Implements or changes REST endpoints in the Java 21 Lambda API (api/) behind the Lambda Function URL, including DTOs, validation, service rules, JDBC repository, and tests. Use when adding or modifying any /api/v1 endpoint or job posting business rule.
---
# Java Lambda endpoint

## Steps
1. Read the endpoint in `docs/specs/job-posting.spec.md` (API contract, lifecycle table, and ACs) and in `docs/api-design.md` if it exists.
2. DTOs: records in `jobposting/dto/` with jakarta.validation annotations that match the spec's field table.
3. Router: register `METHOD /path` in `common/Router`. Path params are parsed there, never in services.
4. Controller: map the request to a DTO, validate it, call the service, and map the result to a response and status code. No rules here.
5. Service: business rules and status transitions. Throw typed exceptions (`NotFoundException`, `InvalidStatusTransitionException`, `VersionConflictException`, `ValidationException`).
6. Repository: JDBC with prepared statements. Filter out `deleted_at IS NULL` rows, use optimistic locking on `version`, and map sort fields through a whitelist.
7. Error mapping: make sure every exception maps to the problem+json `type` and status the spec defines.
8. Tests:
   - Service unit tests (Mockito repository), one per rule and transition
   - Controller tests with a sample Function URL v2 event JSON in `src/test/resources/events/`
   - Repository `*IT` tests with Testcontainers PostgreSQL 17, applying the migrations from `db/migrations`
9. Run `mvn -q test` (and `mvn -q verify` if Docker is available). Fix any failures before reporting.

## Handler template
```java
public final class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final AppContext CTX = AppContext.create(); // DataSource, mapper, router: once per container
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context lambda) {
        return CTX.router().dispatch(event, lambda.getAwsRequestId());
    }
}
```

## Done when
- `api/README.md` exists (create it in this task if missing) with: prerequisites and versions, first-time setup, numbered steps to run the API locally against Docker PostgreSQL, the environment variables, test and package commands, and troubleshooting
- Every endpoint has a positive test and the negative tests that apply: validation and its boundaries, forbidden transition, unknown id, stale version, malformed JSON, bad query parameters
- All API-facing ACs (AC-2, 4, 5, 6, 7, 8, 9, 10, 11) have tests that pass
- No SQL string concatenation and no logging of request bodies
- `mvn -q package -DskipTests` produces `target/api.jar`

# API · Java 21 Lambda (job posting)

Plain Java 21 on AWS Lambda (`java21`, arm64), called through a Lambda Function URL (payload format 2.0).
Libraries: Jackson, Hibernate Validator (jakarta.validation), HikariCP (pool size 2), PostgreSQL JDBC, Flyway, SLF4J + Logback JSON.
Requires JDK 21 and Maven 3.9+.

## Commands
`mvn -q test` · `mvn -q verify` (includes Testcontainers repository tests, needs Docker) · `mvn -q package -DskipTests` → `target/api.jar`

## Structure
```
src/main/java/com/hr/recruitment/
  ApiHandler.java                 Function URL entry point → Router
  MigrationHandler.java           runs Flyway (deployed as the db-migrate Lambda)
  common/                         Router, ProblemDetail, JSON, request logging, DataSource factory
  jobposting/
    JobPostingController.java     HTTP ↔ DTO, no business logic
    JobPostingService.java        rules and status transitions
    JobPostingRepository.java     JDBC, prepared statements only
    dto/  model/                  request/response records, domain types
src/main/resources/db/migration/  copied from ../db/migrations at build time
src/test/java/...                 JUnit 5 + Mockito; *IT.java uses Testcontainers
```

## Config (environment variables)
`DB_HOST`, `DB_PORT` (5432), `DB_NAME`, `DB_SECRET_ARN`, `ALLOWED_ORIGIN`. Credentials come from Secrets Manager on cold start and are cached for the life of the container.

Rules: `.claude/rules/java-api.md` · Skill: `java-lambda-endpoint`
Contract: @../docs/specs/job-posting.spec.md (and `docs/api-design.md` once it exists)

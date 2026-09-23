# API · Java 21 Lambda (candidate capture)

Plain Java 21, called through a Lambda Function URL (payload format 2.0) in AWS, or through `local/LocalServer.java` (Javalin) for local development. Implements the candidate module (`docs/specs/candidate.spec.md`): `POST /api/v1/candidates`, `GET /api/v1/candidates/{id}`, `PUT /api/v1/candidates/{id}`.

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop (for Testcontainers repository tests) | any recent version, running | `docker ps` |

This machine has portable installs at `C:\Users\manik\tools\jdk-21.0.12.1+1` and `C:\Users\manik\tools\apache-maven-3.9.9`; open a new terminal so `JAVA_HOME`/`PATH` pick them up, or export them manually:
```
export JAVA_HOME=/c/Users/manik/tools/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:/c/Users/manik/tools/apache-maven-3.9.9/bin:$PATH"
```

## First-time setup

Nothing to scaffold — `pom.xml` and the source tree already exist in this repo. `db/migrations/` is the single source of truth for the schema; Maven copies it onto the classpath automatically on every build (`pom.xml`'s `<resources>` block adds `../db/migrations` at `db/migration`), so the app and the Testcontainers tests always apply the same file with no manual copy step.

## Run, step by step (local, against Docker Postgres)

1. Start a local database (see `db/README.md` for the full version):
   ```
   docker run --name hr-pg -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=hr -p 5433:5432 -d postgres:17
   docker cp ../db/migrations/V1__create_candidate.sql hr-pg:/tmp/V1.sql
   docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -d hr -f /tmp/V1.sql
   ```
2. Package the JAR:
   ```
   mvn -q package -DskipTests
   ```
   Expected: `target/api.jar` is created (a shaded/uber JAR).
3. Run the local server:
   ```
   export DB_HOST=localhost DB_PORT=5433 DB_NAME=hr DB_USER=postgres DB_PASSWORD=dev PORT=8080
   java -Duser.timezone=UTC -cp target/api.jar com.hr.recruitment.local.LocalServer
   ```
   Expected: `Local API listening on http://localhost:8080`. `-Duser.timezone=UTC` avoids a PostgreSQL JDBC startup error on machines whose OS timezone name (e.g. `Asia/Calcutta`) the container's Postgres build does not recognise.
4. Exercise it:
   ```
   curl -i -X POST http://localhost:8080/api/v1/candidates \
     -H "Content-Type: application/json" \
     -d '{"fullName":"Test Candidate","email":"test@example.com","phone":"9876543210","location":"Bengaluru","totalExperienceYears":5,"highestQualification":"BACHELORS","skills":["Java","SQL"]}'
   ```
   Expected: `201 Created`, a `Location` header, and a JSON body with `status: "ACTIVE"`, `version: 1` (per the spec, a new candidate starts at version 1; `PUT` increments it).
5. Confirm a failing request returns `application/problem+json`:
   ```
   curl -i -X POST http://localhost:8080/api/v1/candidates \
     -H "Content-Type: application/json" \
     -d '{"fullName":"A","email":"not-an-email","phone":"1","location":"B","totalExperienceYears":50,"highestQualification":"XX","skills":[]}'
   ```
   Expected: `400`, `Content-Type: application/problem+json`, and an `errors[]` array naming each invalid field.

## Test and build commands

| Command | What it runs |
|---|---|
| `mvn -q test` | Unit tests only (service + controller). No Docker needed. |
| `mvn verify` | Unit tests plus the Testcontainers repository `*IT` test, which starts a real PostgreSQL 17 container and applies `db/migrations`. Needs Docker reachable from the JVM (see Troubleshooting). |
| `mvn -q package -DskipTests` | Produces `target/api.jar` without running tests. |

Current status on this machine: `mvn test` passes (21/21). `mvn verify`'s Testcontainers step could not be run from this session's shell — see Troubleshooting. It ran successfully as a compiled test class and only needs a normal terminal with Docker API access.

## Config (environment variables)

| Variable | Local value | AWS value |
|---|---|---|
| `DB_HOST` | `localhost` | RDS endpoint |
| `DB_PORT` | `5433` (or your container's port) | `5432` |
| `DB_NAME` | `hr` | as provisioned |
| `DB_USER` | `postgres` | not used — `DB_SECRET_ARN` supplies credentials |
| `DB_PASSWORD` | `dev` | not used locally in AWS mode |
| `DB_SECRET_ARN` | unset | the RDS-managed secret ARN — when set, credentials come from Secrets Manager instead of `DB_USER`/`DB_PASSWORD` |
| `PORT` | `8080` (LocalServer only) | n/a (Function URL) |

Credentials are never logged. `fullName`, `email`, `phone`, `summary`, and `currentEmployer` are never written to any log line (candidate spec, non-functional requirements).

## Structure

```
src/main/java/com/hr/recruitment/
  ApiHandler.java                 Function URL entry point → Router
  common/                         Router, ProblemDetail, Json, RequestLog, DataSourceFactory, AppContext
  candidate/
    CandidateController.java      HTTP ↔ DTO, no business logic
    CandidateService.java         rules: skills uniqueness, ACTIVE/ARCHIVED guard, optimistic locking
    CandidateRepository.java      JDBC, prepared statements only
    dto/  model/                  request/response records, domain types
  local/LocalServer.java          runs the app as a plain HTTP server (Javalin), no AWS needed
src/main/resources/db/migration/  copied from ../db/migrations
src/test/java/...                 JUnit 5 + Mockito; CandidateRepositoryIT uses Testcontainers
```

There is no job posting code yet in this repo — the `common/` layer above was built as the minimal shared foundation for the candidate module and is meant to be reused, not duplicated, when job posting is implemented.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `FATAL: invalid value for parameter "TimeZone"` on startup | Add `-Duser.timezone=UTC` to the `java` command (see step 3). |
| `mvn verify`'s Testcontainers test fails with "Could not find a valid Docker environment" even though `docker ps` works | Testcontainers talks to the Docker Engine API directly (via the npipe on Windows), not through the `docker` CLI. Some sandboxed/restricted shells allow the CLI but block direct API access — run `mvn verify` from a normal terminal (PowerShell, Git Bash outside the harness, or WSL) with Docker Desktop running. Setting `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` can help if the default context is wrong. |
| `docker cp` reports a Windows path that doesn't exist | On Git Bash, prefix the command with `MSYS_NO_PATHCONV=1`, or run from PowerShell/WSL. |
| `target/api.jar` is larger than the ~20 MB target in `docs/proposal.md` §5.2 (R3) | The local dev server (`io.javalin` + embedded Jetty) is bundled into the same shaded JAR that deploys to Lambda, adding several MB it does not need at runtime. Splitting `local/` into a separate Maven module (or a `dev` classifier build) would remove it from the deployed artifact; not done yet in this slice. |
| `400` on `highestQualification` value that looks plausible | The enum only accepts `HIGH_SCHOOL`, `DIPLOMA`, `BACHELORS`, `MASTERS`, `DOCTORATE` (exact case). |

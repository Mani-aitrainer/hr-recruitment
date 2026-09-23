# DB · PostgreSQL 17 migrations

Versioned SQL migrations applied by Flyway. The API build copies `db/migrations/` into the JAR, and the `db-migrate` Lambda runs them from inside the VPC (the database is private). Locally, migrations can also be applied directly with `psql` or the Flyway CLI/Docker image.

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| Docker Desktop | any recent version, running | `docker ps` |
| `psql` (optional, for manual checks) | any | `psql --version` |

No local PostgreSQL install is required — a container is used for everything below.

## Layout
```
migrations/V1__create_candidate.sql   candidate table, constraints, and indexes (candidate spec v0.1)
seed/dev_candidates.sql               synthetic sample candidates for local dev — never run in prod
checks.sql                            one valid insert plus one rejected insert per constraint
```

There is no job posting migration yet; `V1` is the candidate table because that module was built first in this repo.

## Run, step by step

1. Start a local PostgreSQL 17 container:
   ```
   docker run --name hr-pg -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=hr -p 5433:5432 -d postgres:17
   ```
   Expected: a container id is printed. Port `5433` is used locally to avoid clashing with any other Postgres container on `5432`; adjust `DB_PORT` accordingly if you use a different port.
2. Wait for the database to accept connections:
   ```
   docker exec hr-pg pg_isready -U postgres
   ```
   Expected: `accepting connections`.
3. Apply the migration:
   ```
   docker cp db/migrations/V1__create_candidate.sql hr-pg:/tmp/V1.sql
   docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -d hr -f /tmp/V1.sql
   ```
   Expected: `CREATE FUNCTION`, `CREATE TABLE`, `ALTER TABLE`, `CREATE INDEX` (x2), no errors.
4. (Optional) Load synthetic sample data:
   ```
   docker cp db/seed/dev_candidates.sql hr-pg:/tmp/seed.sql
   docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -d hr -f /tmp/seed.sql
   ```
5. Confirm the table exists:
   ```
   docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -d hr -c "\d candidate"
   ```

### Re-running on an empty database

To prove the migration is repeatable from scratch:
```
docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -c "DROP DATABASE hr;"
docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -c "CREATE DATABASE hr;"
docker cp db/migrations/V1__create_candidate.sql hr-pg:/tmp/V1.sql
docker exec -e PGPASSWORD=dev hr-pg psql -U postgres -d hr -f /tmp/V1.sql
```
Expected: the same clean output as step 3, with no errors.

### Proving the constraints

`db/checks.sql` has one valid insert followed by one rejected insert per constraint (short `full_name`, malformed `email`, out-of-range `total_experience_years`, out-of-range `notice_period_days`, unknown `highest_qualification`, empty/oversized/too-long `skills`, oversized `summary`, unknown `status`). Run each statement individually and confirm the valid one succeeds and every other one raises an error — running the whole file at once stops at the first error.

## Test and build commands

There is no build step for this folder. "Testing" a migration means applying it to an empty database (above) and running `checks.sql`.

## Adding a new migration

- Next file is `V2__{snake_case_description}.sql`. Never edit `V1` — it may already be applied.
- Add `CHECK`/`NOT NULL` constraints for every new field rule, and an index for any new filter or sort.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `port is already allocated` on `docker run` | Another container is using the port. Either stop it or change `-p <port>:5432` and the connection string. |
| `cannot use subquery in check constraint` | PostgreSQL `CHECK` constraints cannot contain a `SELECT`. Wrap the logic in an `IMMUTABLE` SQL function and call the function from the `CHECK`, as `candidate_skills_item_lengths_ok` does. |
| `docker cp` reports a Windows path that doesn't exist (e.g. `C:\Users\...\Temp\V1.sql`) | On Git Bash, the destination path inside the container gets reinterpreted as a Windows path. Prefix the command with `MSYS_NO_PATHCONV=1` or run it from PowerShell/WSL instead. |
| `psql: FATAL: password authentication failed` | Set `PGPASSWORD=dev` (matches `POSTGRES_PASSWORD` above) on the `docker exec` call. |

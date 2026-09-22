# DB · PostgreSQL 17 migrations

Versioned SQL migrations applied by Flyway. The API build copies `db/migrations/` into the JAR, and the `db-migrate` Lambda runs them from inside the VPC (the database is private).

## Layout
```
migrations/V1__create_job_posting.sql     schema changes, applied once, in order
seed/dev_job_postings.sql                 optional sample data for dev, never run in prod
```

## Run
- AWS: `aws lambda invoke --function-name <migrate_function_name> out.json` (name from `terraform output`)
- Local: `docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=dev postgres:17`, then `cd api && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/postgres -Dflyway.user=postgres -Dflyway.password=dev`

## Conventions
- File name: `V{next integer}__{snake_case_description}.sql`
- snake_case tables and columns. The table is `job_posting`, singular.
- Enforce spec rules with constraints (`CHECK`, `NOT NULL`) as well as in the API

Rules: `.claude/rules/sql-migrations.md` · Skill: `db-migration`
Data model: @../docs/specs/job-posting.spec.md

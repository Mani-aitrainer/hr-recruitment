---
name: db-migration
description: Creates PostgreSQL 17 Flyway migrations in db/migrations for the HR recruitment schema, derived from the spec or API design. Use when adding tables, columns, constraints, or indexes, or when the data model in the spec changes.
---
# DB migration

## Steps
1. Read the data model in `docs/specs/job-posting.spec.md` (and `docs/api-design.md` if it exists).
2. List `db/migrations/` and pick the next version number. Never modify an existing file.
3. Write `V{n}__{snake_case_description}.sql`:
   - Tables and columns in snake_case, with `timestamptz` for times and `uuid DEFAULT gen_random_uuid()` for keys
   - `CHECK` constraints for every enum and range in the spec
   - Indexes for list filters and sorts, partial `WHERE deleted_at IS NULL` where useful
   - A short header comment: what the migration does and which spec version it matches
4. If the change affects the API, list the Java DTO, repository, and UI model files that must change next.
5. Validate locally when Docker is available (see `db/CLAUDE.md`). Apply twice from an empty DB to confirm it runs in order.

## Example: V1
```sql
-- V1: job_posting table (spec v0.1)
CREATE TABLE job_posting (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title            varchar(100) NOT NULL CHECK (char_length(title) BETWEEN 5 AND 100),
  department       varchar(40)  NOT NULL CHECK (department IN ('ENGINEERING','HUMAN_RESOURCES','FINANCE','SALES','MARKETING','OPERATIONS')),
  location         varchar(100) NOT NULL,
  employment_type  varchar(20)  NOT NULL CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT')),
  experience_min   smallint     NOT NULL CHECK (experience_min BETWEEN 0 AND 40),
  experience_max   smallint     NOT NULL CHECK (experience_max BETWEEN 0 AND 40),
  skills           text[]       NOT NULL CHECK (cardinality(skills) BETWEEN 1 AND 15),
  description      text         NOT NULL CHECK (char_length(description) BETWEEN 50 AND 5000),
  openings         smallint     NOT NULL CHECK (openings BETWEEN 1 AND 100),
  closing_date     date         NOT NULL,
  status           varchar(12)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','CLOSED')),
  version          integer      NOT NULL DEFAULT 0,
  created_at       timestamptz  NOT NULL DEFAULT now(),
  updated_at       timestamptz  NOT NULL DEFAULT now(),
  published_at     timestamptz,
  closed_at        timestamptz,
  deleted_at       timestamptz,
  CONSTRAINT experience_range CHECK (experience_min <= experience_max)
);
CREATE INDEX job_posting_status_updated_idx ON job_posting (status, updated_at DESC) WHERE deleted_at IS NULL;
```

## Done when
- `db/README.md` exists (create it in this task if missing) with: prerequisites, how to start a local PostgreSQL 17 container, numbered steps to apply and verify the migrations locally and in AWS, how to add a migration, and troubleshooting
- The migration applies cleanly on an empty PostgreSQL 17 database
- Every spec rule that can be a constraint is one

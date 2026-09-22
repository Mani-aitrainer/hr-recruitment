---
paths:
  - "db/**/*.sql"
  - "api/src/main/resources/db/**"
---
# SQL migration rules

- Never edit a migration that has been applied anywhere. Add a new `V{n+1}__...sql` instead.
- One logical change per migration. It must be safe to run inside a single transaction.
- snake_case names. Primary keys are `uuid DEFAULT gen_random_uuid()`. All timestamps are `timestamptz`.
- Enforce spec rules in the database too: `NOT NULL`, `CHECK` for enums and ranges, `CHECK (experience_min <= experience_max)`.
- Add an index for every column used in a list filter or sort (`status`, `updated_at`). Use a partial index `WHERE deleted_at IS NULL` where it helps.
- No `DROP` or destructive `ALTER` without an explicit note in the PR and a human's approval.
- Seed data goes only in `db/seed/` and must never contain real personal data.

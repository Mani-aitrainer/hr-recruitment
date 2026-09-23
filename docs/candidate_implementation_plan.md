# Candidate capture module — plan

## Context

You want a screen that captures a candidate's basic and professional information. Candidates are currently **excluded** from v1: `docs/proposal.md` §3 lists "Candidates, applications, interviews, and offers" as out of scope, and `docs/specs/job-posting.spec.md` repeats it. Nothing is implemented yet either — the repo is docs plus the `.claude/` harness, and `docs/implementation-plan.md` shows P1–P10 all "not started".

So this is a **new module with its own spec**, added after job posting ships, following the project's `proposal → plan → spec → cross-model review → implementation` order. This plan writes the documents first and defines the implementation slice that follows.

## Decisions from the brainstorm

| # | Question | Decision |
|---|---|---|
| 1 | Where does it sit? | New v2 spec (`docs/specs/candidate.spec.md`), built after P5 |
| 2 | Linked to job posts? | No — standalone talent pool, no FK to `job_posting` |
| 3 | Field depth | Flat summary fields, one table, no child tables |
| 4 | Resume / CV | Not in v1 — keeps "file uploads" out of scope |
| 5 | Lifecycle | `ACTIVE` / `ARCHIVED` only |
| 6 | PII on a public URL | Synthetic data only, keep no-auth, record as a new risk |
| 7 | Duplicates | No uniqueness rule on email or phone |
| 8 | Screens | Capture form only |
| 9 | API surface | `POST /`, `GET /{id}`, `PUT /{id}` |
| 10 | Sensitive fields | None — no DOB, no salary, no gender |

### Consequences to accept knowingly

These follow from the choices above and are written into the spec as explicit limits, not left as surprises:

- **`ARCHIVED` is unreachable in v1.** Archiving needs `PATCH /{id}/status`, which is not in the API surface, and a detail screen to trigger it. The column exists with a `CHECK` constraint and defaults to `ACTIVE`; the transition ships with the detail screen later.
- **No list screen means no browse path.** After saving, the form shows a confirmation and resets. An existing candidate is reachable only by direct URL (`/candidates/:id/edit`).
- **No uniqueness means no duplicate conflict.** The only `409` is `version-conflict` from `PUT`. Two identical candidates are allowed.
- **Assumption:** `noticePeriodDays` is kept as a professional field. It was not one of the sensitive options you ruled out (salary, DOB, gender). Say so if you want it dropped.

## Field set

Mirrors `job_posting` conventions exactly so validators, constraints, and test shapes carry straight over.

**Basic**

| Field | Type | Rule |
|---|---|---|
| `fullName` | string | required, trimmed, 2–100 chars |
| `email` | string | required, valid email, ≤ 255 chars, stored lowercase |
| `phone` | string | required, 8–20 chars, digits with optional `+`, space, `-` |
| `location` | string | required, 2–100 chars (same rule as the job post's `location`) |

**Professional**

| Field | Type | Rule |
|---|---|---|
| `currentEmployer` | string | optional, 2–100 chars when present |
| `currentTitle` | string | optional, 2–100 chars when present |
| `totalExperienceYears` | integer | required, 0–40 (same range as the job post's experience) |
| `noticePeriodDays` | integer | optional, 0–180 |
| `highestQualification` | enum | required — `HIGH_SCHOOL` \| `DIPLOMA` \| `BACHELORS` \| `MASTERS` \| `DOCTORATE` |
| `skills` | string[] | 1–15 items, each 1–30 chars, case-insensitive unique — **identical rule to `job_posting.skills`** |
| `summary` | string | optional, ≤ 2000 chars, plain text |

**Server-set, read-only:** `id`, `status`, `createdAt`, `updatedAt`, `archivedAt`, `deletedAt`, `version`.

## Deliverables

### C0 · Documents (do this first, before any code)

1. **`docs/proposal.md`** — move candidates out of §3 "Out of scope" into a named v2 module, add risk **R7: candidate PII on an unauthenticated public Function URL**, mitigated by synthetic data only, no PII in logs, and auth as the next step. Record the ten decisions in §14 / the decisions log.
2. **`docs/specs/candidate.spec.md`** (new, v0.1) — same shape as `job-posting.spec.md`: goal, out of scope, roles, user stories, the field table above, the `ACTIVE`/`ARCHIVED` lifecycle, the three-endpoint API contract reusing the RFC 7807 error shape, the single screen, and acceptance criteria `CAND-1`…`CAND-8` (positive and negative, per the test rule).
3. **`docs/implementation-plan.md`** — add phases **C1–C4** after P10, plus rows in §5 Tracking and §6 Decisions log.
4. Run the `cross-model-reviewer` agent on the new spec, table the findings, decide each, apply the accepted ones, then bump the spec version.

**No infrastructure change.** The candidate module reuses the existing Lambda, RDS instance, Function URL, and S3 bucket. `iac/` is untouched.

### C1 · Database

- `db/migrations/V2__create_candidate.sql` — table `candidate` (singular, snake_case), `CHECK` constraints for every enum and range in the field table, `status IN ('ACTIVE','ARCHIVED')` defaulting to `ACTIVE`, `deleted_at` for soft delete, `version integer`.
- Per `.claude/rules/sql-migrations.md`: never edit an applied migration; V1 (job posting) stays as it is.
- Prove constraints with one valid insert plus one rejected insert per constraint — short `fullName`, bad email, experience 41, notice period 181, unknown qualification, empty and 16-item `skills`, unknown `status`.
- Use the `db-migration` skill. **Update** `db/README.md` (it will already exist from P3).

### C2 · Java API

- New package `api/src/main/java/com/hr/recruitment/candidate/` — `CandidateController`, `CandidateService`, `CandidateRepository`, `dto/`, `model/`, following the Handler → Router → Controller → Service → Repository layering in `.claude/rules/java-api.md`.
- Register three routes on the existing `common/Router` at `/api/v1/candidates`: `POST /`, `GET /{id}`, `PUT /{id}`.
- Reuse from job posting rather than rewriting: `common/ProblemDetail`, the JSON setup, request logging, and the `DataSource` factory. Optimistic locking in the repository, matching the job posting pattern.
- Prepared statements only. Never log `fullName`, `email`, or `phone`.
- Use the `java-lambda-endpoint` skill. Tests positive **and** negative per the test rule: every field boundary on both sides, unknown id → `404`, stale `version` → `409 version-conflict`, malformed JSON → `400`. Testcontainers `*IT` test applying `db/migrations`.
- **Update** `api/README.md`.

### C3 · Angular capture form

- `ui/src/app/features/candidate/` — `candidate.routes.ts` (lazy), `data/candidate.model.ts`, `data/candidate.service.ts`, `form/` component. Routes: `/candidates/new` and `/candidates/:id/edit`.
- Reuse the shared chip input built for job posting skills (`ui/src/app/shared/`) — the `skills` rule is identical.
- Reactive form, validators mirroring the field table exactly. Map `400` `errors[].field` onto controls and handle `409 version-conflict` with a reload prompt, as job posting does.
- All labels in `src/i18n/en.json`. Every input labelled, errors wired with `aria-describedby`, keyboard-only usable.
- Use the `job-posting-ui` skill's patterns. Tests positive and negative per component and for the service, named after their `CAND-` criterion.
- **Update** `ui/README.md`.

### C4 · Review

Run the `code-reviewer` agent across the diff; fix everything Critical and High. Confirm no PII appears in any log line, seed file, or test fixture.

## Critical files

| File | Change |
|---|---|
| `docs/specs/candidate.spec.md` | new |
| `docs/proposal.md` | scope §3, risk R7, decisions log |
| `docs/implementation-plan.md` | phases C1–C4, tracking, decisions |
| `db/migrations/V2__create_candidate.sql` | new |
| `api/.../candidate/` (5 files + dto/model) | new |
| `api/.../common/Router.java` | register 3 routes |
| `ui/src/app/features/candidate/` | new |
| `db/README.md`, `api/README.md`, `ui/README.md` | update |

## Verification

1. `docker run --name hr-pg -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=hr -p 5432:5432 -d postgres:17`, apply `db/migrations` with Flyway, confirm the `candidate` table and every constraint exists. Drop and recreate to prove it runs on an empty database.
2. `cd api && mvn -q verify` — Testcontainers tests green, Docker running.
3. Run `local/LocalServer.java`; `curl` a valid `POST /api/v1/candidates` → `201`, then one invalid body → `400` in `application/problem+json`, then a `PUT` with a stale `version` → `409 version-conflict`.
4. `cd ui && npm run lint && npm test -- --watch=false && npm run build`.
5. `npm start`, fill the form against the local API: one valid save, one invalid submission showing inline errors, and a tab-only pass through every field.
6. Every `CAND-` criterion has a passing test named after it, positive and negative.

**Definition of done** (root `CLAUDE.md`): `mvn -q test` and `npm test -- --watch=false` pass, the three READMEs match what was actually run, and `code-reviewer` reports no Critical or High findings. `iac/` is unchanged, so no `terraform` gate applies.

## Sequencing note

C1–C4 depend on P3–P5 being finished, because they extend the Router, the shared components, and the migration chain that those phases create. If you want the candidate form sooner than that, the honest options are to swap the module order in the proposal, or to build C1–C3 standalone and accept the merge work later. Say which, and I will adjust before starting.

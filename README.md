# HR Recruitment

A monorepo for the HR Recruitment system. The first module implemented is **candidate capture** (`docs/specs/candidate.spec.md`) — a form where recruiters record a candidate's basic and professional information. The **job posting** module (`docs/specs/job-posting.spec.md`) is specified but not yet built.

See `docs/proposal.md` for the why, `docs/implementation-plan.md` for phase status, and `CLAUDE.md` for how this repo is organised for agent-assisted work.

## Layout

| Folder | What | README |
|---|---|---|
| `db/` | PostgreSQL 17 migrations (candidate table, V1) | [`db/README.md`](db/README.md) |
| `api/` | Java 21 Lambda API: `POST/GET/PUT /api/v1/candidates` | [`api/README.md`](api/README.md) |
| `ui/` | Angular 22 candidate capture form | [`ui/README.md`](ui/README.md) |
| `iac/` | Terraform (not yet written — the candidate module reuses no infrastructure of its own; see `docs/specs/candidate.spec.md`) | — |
| `docs/` | Proposal, plan, specs | — |

## Run everything locally, in order

1. **Database** — start Postgres and apply the migration. Full steps: [`db/README.md`](db/README.md).
2. **API** — package the JAR and run `LocalServer` against that database. Full steps: [`api/README.md`](api/README.md).
3. **UI** — install dependencies (Node 22.22.3+ required — see [`ui/README.md`](ui/README.md) Troubleshooting) and run `npm start` against the local API. Full steps: [`ui/README.md`](ui/README.md).

Each README's "Run, step by step" section has the exact commands and their expected output.

## Status

- ✅ Candidate module: database, API, and UI implemented and tested locally (`db/`, `api/`, `ui/`).
- ⏳ Job posting module: specified, not implemented.
- ⏳ `iac/`: not started. No AWS resources exist yet; nothing here has been deployed.

See `docs/implementation-plan.md` §5 for the full phase tracker.

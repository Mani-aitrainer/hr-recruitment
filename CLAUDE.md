# HR Recruitment · Job posting (monorepo)

Recruiters create, edit, publish, and close job posts. v1 covers **only the job posting screen**.
Read `docs/proposal.md` for the why, `docs/implementation-plan.md` for what to build next, and the specs for the what:
- @docs/implementation-plan.md
- @docs/specs/job-posting.spec.md
- @docs/specs/infrastructure.spec.md

## Layout
| Folder | What | Stack | Details |
|---|---|---|---|
| `ui/` | Angular app, hosted on S3 | Angular 22 (`@angular/cli@22.1.8`), signals, SCSS | `ui/CLAUDE.md` |
| `api/` | REST API on Lambda + Function URL | Java 21, Maven, JDBC, Flyway | `api/CLAUDE.md` |
| `db/` | SQL migrations for PostgreSQL 17 | Flyway naming | `db/CLAUDE.md` |
| `iac/` | AWS infrastructure | Terraform ≥ 1.10, AWS provider 6.x | `iac/CLAUDE.md` |
| `docs/` | Proposal, plan, specs, API design | Markdown | — |

## Process (always)
proposal → implementation plan → spec → cross-model review → implementation.
- Update the spec **before** changing behaviour in code.
- If a request is not covered by the spec, or conflicts with it, stop and ask. Do not guess.
- Do not build anything from the spec's "Out of scope" list.

## Cross-layer changes
Adding or changing a field touches, in this order:
1. `docs/specs/job-posting.spec.md`
2. a new migration in `db/migrations/`
3. the Java DTO, validation, and repository in `api/`
4. the Angular model, form, and validators in `ui/`
5. tests in both `api/` and `ui/`

## README (write it with the code, not before)
There are no README files yet, because no project is implemented yet. **The first implementation change in a folder must also create that folder's `README.md`**, and a root `README.md` when the first project lands. Each one contains:
1. **Prerequisites**: every tool with its minimum version and a command to check it
2. **Setup**: first-time steps, including scaffolding and configuration
3. **Run, step by step**: numbered commands with the expected result of each
4. **Test and build commands**
5. **Troubleshooting**: the common failures and their fixes

Write only steps you have actually run or taken from the spec. Update the README whenever a command, prerequisite, or environment variable changes.

## Definition of done
- Relevant tests pass (`cd api && mvn -q test`, `cd ui && npm test -- --watch=false`)
- `terraform fmt -check -recursive` and `terraform validate` pass if `iac/` changed
- The folder's `README.md` exists and its steps match what you ran
- The `code-reviewer` agent reports no Critical or High findings

## Never
- Commit secrets, `.env`, `*.tfvars`, or Terraform state
- Run `terraform apply`/`destroy` or `git push`. A human does these.
- Edit an already-applied migration. Add a new one instead.

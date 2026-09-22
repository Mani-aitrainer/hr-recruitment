# Implementation plan · HR Recruitment job posting

| | |
|---|---|
| Status | Draft, phases 0–1 partly done |
| Source | `docs/proposal.md` |
| Specs | `docs/specs/job-posting.spec.md`, `docs/specs/infrastructure.spec.md` |
| Target | A deployed job posting screen in AWS `us-east-1`, with every acceptance criterion covered by a test |
| Last updated | 2026-09-22 |

---

## 1. How to use this plan

- Work **one phase at a time**, in order. Each phase lists what it depends on, so nothing starts before its inputs exist.
- Every phase ends with a **Done when** checklist. Do not start the next phase until it passes.
- **Claude may run:** tests, builds, `terraform plan`, `fmt`, `validate`, and read-only AWS calls.
- **A human runs:** `terraform apply`, `terraform destroy`, `aws lambda invoke` for migrations, `aws s3 sync`, and `git push`. These are blocked for Claude in `.claude/settings.json`.
- If a phase turns out to contradict a spec, **stop and update the spec first**, then continue.

### The README rule (applies to phases 3–7)

No README files exist yet, and none should be written ahead of the code. The **first implementation change in a folder must also create that folder's `README.md`**, containing:

1. Prerequisites: each tool, its minimum version, and a command to check it
2. First-time setup, including scaffolding and configuration
3. Numbered run instructions, with the expected result of each step
4. Test and build commands
5. Troubleshooting for the failures you actually hit

A root `README.md` is added with the first project (phase 3) and links the folder READMEs together. Write every step from a command you have actually run.

### The test rule (applies to phases 3–5)

**Every unit of behaviour gets both a positive and a negative unit test, written in the same task as the code.** Details and the per-layer coverage table are in `docs/proposal.md` §5.5. At a high level, each endpoint, component, and constraint needs:

| | What to write |
|---|---|
| **Positive** | Valid input → the expected result and state change |
| **Negative** | Invalid field values (including the boundaries on both sides), forbidden status transitions, unknown id, stale `version`, and malformed input |

Name each test after what it proves, and after its acceptance criterion where there is one (`AC-6 rejects edit of published post with 409`). A phase is not done if only the happy path is covered. Phases 6 and 7 have no unit tests; their equivalent is `terraform validate`, a reviewed `plan`, and the IAC criteria in P9.

### Mapping to the bootcamp demo roadmap

The slide deck lists the demo in a presentation order (UI before API). This plan uses dependency order instead, so each layer can be tested as it is built.

| Demo step | Plan phase |
|---|---|
| 01, 02 Repo, monorepo, harness | P0 |
| 03 Proposal → plan → spec, review | P1 |
| 04 API design | P2 |
| 09 DB script | P3 |
| 06 Java API | P4 |
| 05 Angular screen | P5 |
| 07, 08 Terraform | P6, P7 |
| — Deploy and verify | P8, P9 |
| 10 Review | P10 |

---

## 2. Prerequisites

Tooling must be in place before phase 3. See `docs/proposal.md` §13 for the full table.

| Tool | Needed from | Status on this machine |
|---|---|---|
| Git | P0 | installed |
| JDK 21 + Maven 3.9+ | P4 | ✓ Temurin 21.0.12.1 and Maven 3.9.9, portable under `C:\Users\manik\tools` |
| Node.js 22.12+ | P5 | **Node 16.14 → still to upgrade** |
| Docker Desktop | P3 | ✓ server 29.5.2, running |
| Terraform 1.10+ | P6 | ✓ 1.16.3 |
| AWS CLI v2 + account access | P6 | ✓ installed; confirm the profile with `aws sts get-caller-identity` |

Only the Node.js upgrade is left, and it blocks P5 alone. New terminals pick up `JAVA_HOME` and the `PATH` entries; terminals opened before the install do not.

---

## 3. Phase overview

| # | Phase | Depends on | Output | Effort |
|---|---|---|---|---|
| P0 | Foundations: monorepo, docs, agent harness | — | `.claude/`, `docs/`, CLAUDE.md files | done |
| P1 | Documents finalised and cross-model reviewed | P0 | reviewed proposal, plan, specs | 0.5 day |
| P2 | API design | P1 | `docs/api-design.md` | 0.5 day |
| P3 | Database schema and migration V1 | P2 | `db/migrations/V1__…sql`, `db/README.md`, root `README.md` | 0.5 day |
| P4 | Java API implementation | P3 | `api/` code and tests, `api/README.md` | 2 days |
| P5 | Angular UI implementation | P4 | `ui/` code and tests, `ui/README.md` | 2 days |
| P6 | Terraform: network and database | P1 | `iac/modules/{network,database}`, `iac/README.md` | 1 day |
| P7 | Terraform: IAM, Lambda, Function URL, S3 | P4, P6 | `iac/modules/{api,frontend}`, `envs/dev` | 1 day |
| P8 | Deploy to AWS and run migrations | P5, P7 | a running environment | 0.5 day |
| P9 | End-to-end verification against the ACs | P8 | a verification report | 0.5 day |
| P10 | Review and hardening | P9 | review findings fixed | 0.5 day |

```
P0 ─▶ P1 ─┬─▶ P2 ─▶ P3 ─▶ P4 ─▶ P5 ──────────┬─▶ P8 ─▶ P9 ─▶ P10
          └─▶ P6 ──────────────▶ P7 ─────────┘
```

---

## 4. Phases in detail

### P0 · Foundations *(done)*

**Goal.** A monorepo the agent can navigate, with the standards it must follow.

**Delivered.** `ui/ api/ db/ iac/ docs/` with a CLAUDE.md in each; root `CLAUDE.md`; `.claude/rules/` (5), `.claude/skills/` (4), `.claude/agents/` (3), `settings.json`, `.mcp.json`; `.gitignore`; user-level `CLAUDE.md`, `log-analyzer` skill, `cross-model-reviewer` agent.

**Remaining.** Commit the work, and create the GitHub repository if the code will be shared.

---

### P1 · Documents finalised and cross-model reviewed

**Goal.** The proposal, this plan, and both specs are correct, consistent, and agreed before any code is written.

**Depends on.** P0

**Steps**
1. Answer the open questions in `docs/proposal.md` §14:
   - plain Java or Spring Boot (§5.2) — the rest of the documents assume **plain Java**
   - whether no authentication is acceptable for the demo (risk R1)
   - departments as a fixed list or a managed table
   - whether a CLOSED post can be reopened (current answer: no)
2. Fold each answer into the proposal and, where it changes behaviour, into the spec.
3. Run the cross-model review: use the `cross-model-reviewer` agent on `docs/proposal.md`, `docs/implementation-plan.md`, and both specs. It needs the Codex CLI or the Gemini CLI installed.
4. Put the findings into a table, decide accept or reject for each, and apply the accepted ones to the documents.
5. Bump the spec version, and record the decisions in the proposal.

**Done when**
- [ ] No open question is unanswered
- [ ] The cross-model review has run and every finding has a decision
- [ ] The documents agree with each other on region, model, statuses, and endpoints

**Risk.** Neither reviewer CLI is installed. Then either install one, or have a person review the documents and note that in the proposal.

---

### P2 · API design

**Goal.** A design precise enough that the Java implementation is mechanical.

**Depends on.** P1

**Steps**
1. Run the `api-designer` agent. It reads the spec and writes `docs/api-design.md`.
2. Review its output against `docs/specs/job-posting.spec.md`, checking in particular:
   - every endpoint maps to a controller and service method
   - the status transition table matches the spec exactly
   - each SQL statement is parameterised, and each has a supporting index
   - the exception → problem `type` → HTTP status mapping is complete
   - the test plan names the AC each test covers
3. Resolve the agent's "Open questions" section. Update the spec if an answer changes behaviour.
4. In parallel, finish the toolchain: JDK 21, Maven, and Docker are in place; upgrade Node.js to 22 LTS and verify with `node -v`.

**Done when**
- [ ] `docs/api-design.md` exists with all 9 sections filled in
- [ ] It has no unresolved open questions
- [ ] `java -version` prints 21, `mvn -v` reports Java 21, `node -v` prints 22+, `docker ps` works

---

### P3 · Database schema and migration V1

**Goal.** A PostgreSQL 17 schema that enforces the spec, runnable locally in Docker.

**Depends on.** P2

**Steps**
1. Use the `db-migration` skill. It reads the data model from the spec and the API design.
2. Write `db/migrations/V1__create_job_posting.sql`: columns, `CHECK` constraints for every enum and range, the `experience_min <= experience_max` constraint, and the filter and sort indexes.
3. Start a local database:
   `docker run --name hr-pg -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=hr -p 5432:5432 -d postgres:17`
4. Apply the migration with the Flyway Docker image, then confirm the table and constraints exist.
5. Drop and recreate the container, then apply again, to prove it runs on an empty database.
6. Optionally add `db/seed/dev_job_postings.sql` with a few sample rows. No real personal data.
7. Prove the constraints (the test rule, §1): insert one valid row, then one row per constraint that must be rejected — short title, unknown department, unknown employment type, experience out of range, `experience_min > experience_max`, empty or oversized skills, short description, `openings` 0 and 101, unknown status. Keep these statements in `db/migrations/../checks.sql` or as the repository `*IT` tests in P4.
8. **Write `db/README.md`** and the root `README.md` (the README rule, §1).

**Done when**
- [ ] The migration applies cleanly to an empty PostgreSQL 17 database
- [ ] Every spec rule that can be a constraint is one, proven by a valid insert that succeeds **and** an invalid insert per constraint that is rejected
- [ ] `db/README.md` and root `README.md` exist, and their steps have been run as written

---

### P4 · Java API implementation

**Goal.** Every endpoint in the spec working locally against the Docker database.

**Depends on.** P3

**Steps**
1. Create `api/pom.xml`: Java 21, shaded JAR `target/api.jar`, and the dependencies listed in `api/CLAUDE.md`.
2. Build `common/`: `Router`, JSON setup, `ProblemDetail`, request logging with `requestId`, and the `DataSource` factory (Secrets Manager in AWS, `DB_PASSWORD` locally).
3. Use the `java-lambda-endpoint` skill for each endpoint, in this order: `POST`, `GET /{id}`, `GET /`, `PUT`, `PATCH /{id}/status`, `DELETE`.
4. Put the status rules in `JobPostingService` only, and enforce optimistic locking in the repository.
5. Add `local/LocalServer.java`, a small HTTP server that maps requests to the `Router`, so the API can run without AWS.
6. Tests, positive and negative for every endpoint (the test rule, §1): service unit tests per rule and transition, controller tests using a sample Function URL v2 event, and Testcontainers `*IT` tests that apply `db/migrations`. Negative cases to cover per endpoint: each field rule and its boundaries, every forbidden status transition, unknown id, stale `version`, malformed JSON, and bad query parameters (`size` above 100, an unknown `sort` field, a non-numeric `page`).
7. Run `mvn -q verify`, then run the API locally and exercise it with curl, including one failing request to confirm the problem+json shape.
8. **Write `api/README.md`** (the README rule, §1).

**Done when**
- [ ] `mvn -q verify` passes, with Docker running
- [ ] Every endpoint has at least one positive test and negative tests for validation, forbidden transition, not found, and conflict, where they apply
- [ ] AC-2, 4, 5, 6, 7, 8, 9, 10, 11 each have a passing test named after the AC
- [ ] No SQL built by string concatenation, and no request bodies or secrets in logs
- [ ] `mvn -q package -DskipTests` produces `target/api.jar`
- [ ] `api/README.md` exists and its steps have been run as written

**Risks.** Testcontainers needs Docker; the JAR must stay under 20 MB (proposal R3).

---

### P5 · Angular UI implementation

**Goal.** The three screens working against the local API.

**Depends on.** P4

**Steps**
1. Scaffold Angular 22 into `ui/` with `npx @angular/cli@22.1.8`, keeping the existing `CLAUDE.md`. Generate into a temporary folder and copy the files in.
2. Add `src/environments/`: `apiBaseUrl` is `http://localhost:8080` for development, and a placeholder for production that the deploy step replaces.
3. Use the `job-posting-ui` skill: model and service first, then the list, form, and detail components, then the lazy routes.
4. Copy the validation rules from the spec's field table exactly, and map `400` problem+json errors onto form controls.
5. Handle `409 version-conflict` with a reload prompt.
6. Tests, positive and negative for every component and the service (the test rule, §1), each named after the AC it covers. Negative cases to cover: an invalid form blocks submission and shows the message, boundary values on `title`, `description`, `openings`, and `experience`, a past `closingDate`, a server `400` mapped onto the right control, a `409` conflict prompt, a `404` on detail, and the empty and error states of the list.
7. Run `npm run lint`, `npm test -- --watch=false`, and `npm run build`. Click through the app against the local API, including one invalid submission.
8. **Write `ui/README.md`** (the README rule, §1).

**Done when**
- [ ] Lint, tests, and build pass
- [ ] Every component and the service have at least one positive and one negative test
- [ ] AC-1, 2, 3, 5, 7, 8, 9, 10, 11 each have a passing test
- [ ] Every label comes from the i18n file, and no API URL is hard-coded
- [ ] Keyboard-only use works, and each input has a label (spec, non-functional requirements)
- [ ] `ui/README.md` exists and its steps have been run as written

---

### P6 · Terraform: network and database

**Goal.** A private VPC with PostgreSQL that nothing outside can reach.

**Depends on.** P1 (it can run in parallel with P2–P5)

**Steps**
1. Create the backend: an S3 bucket for state, and `envs/dev/backend.tf` with `use_lockfile = true`.
2. Use the `terraform-change` skill to write `modules/network`: VPC `10.20.0.0/16`, two private subnets in two AZs, `lambda-sg` and `rds-sg`, and the Secrets Manager interface endpoint with private DNS.
3. Write `modules/database`: subnet group and `aws_db_instance` with the settings in the infrastructure spec, including `manage_master_user_password = true`.
4. Wire both into `envs/dev/main.tf`, add variables and outputs, and commit `terraform.tfvars.example` (never `terraform.tfvars`).
5. `terraform fmt -recursive`, `terraform init`, `terraform validate`, `terraform plan -out tfplan`.
6. Have the `iac-reviewer` agent review the diff and the plan.
7. **Write `iac/README.md`** (the README rule, §1).

**Done when**
- [ ] fmt and validate are clean, and the plan matches the spec's resource table
- [ ] Nothing is publicly accessible, and `rds-sg` allows 5432 only from `lambda-sg`
- [ ] The reviewer reports no Critical or High findings
- [ ] `iac/README.md` exists

---

### P7 · Terraform: IAM, Lambda, Function URL, S3

**Goal.** The compute and hosting layer, with least-privilege IAM.

**Depends on.** P4 (the JAR must exist) and P6

**Steps**
1. `modules/api`: the IAM role with `AWSLambdaVPCAccessExecutionRole` plus `secretsmanager:GetSecretValue` scoped to the RDS secret ARN only; the API Lambda and the `db-migrate` Lambda (same JAR, different handlers); the Function URL with CORS limited to the S3 website origin; log groups with 14-day retention; reserved concurrency 5.
2. `modules/frontend`: the S3 website bucket, its public access settings, the bucket policy, and `error_document = index.html` for Angular routing.
3. Wire both into `envs/dev`, and add the outputs the deploy steps need.
4. fmt, validate, `plan -out tfplan`, then the `iac-reviewer` agent.
5. Update `iac/README.md` with the full deploy order.

**Done when**
- [ ] The plan creates both Lambdas, the Function URL, and the bucket, and destroys nothing
- [ ] No IAM statement uses `"*"` resources except where AWS requires it, with a comment saying so
- [ ] The only public resource is `s3:GetObject` on the UI bucket
- [ ] The reviewer reports no Critical or High findings

---

### P8 · Deploy to AWS and run the migrations

**Goal.** A working environment at the `ui_website_url`.

**Depends on.** P5, P7

**Steps (a human runs steps 2, 3, and 5)**
1. `cd api && mvn -q package`
2. `cd iac/envs/dev && terraform apply tfplan` — about 10–15 minutes, mostly RDS
3. `aws lambda invoke --function-name $(terraform output -raw migrate_function_name) out.json`, then check the result
4. Put `function_url` into `ui/src/environments/environment.ts` and run `npm run build`
5. `aws s3 sync ui/dist/ui/browser s3://$(terraform output -raw ui_bucket_name) --delete`
6. Open the `ui_website_url` output, and check `curl "$(terraform output -raw function_url)api/v1/job-postings"`
7. Record the outputs and any manual step in `iac/README.md`

**Done when**
- [ ] The UI loads from S3 and the list call succeeds with no CORS error
- [ ] The schema exists in RDS (check the CloudWatch logs of the migrate Lambda)
- [ ] The first cold start is under 3 seconds (otherwise see risk R3)

**Rollback.** `terraform destroy`, fix, and apply again. There is no production data at this stage.

---

### P9 · End-to-end verification

**Goal.** Evidence that the deployed system meets the spec.

**Depends on.** P8

**Steps**
1. Walk through AC-1 to AC-11 in the browser against the deployed environment, and note the result of each.
2. Check the infrastructure criteria IAC-0 to IAC-6, including that RDS cannot be reached from outside the VPC.
3. Check the non-functional requirements: p95 latency, keyboard-only use, labels and `aria-describedby`, and one structured log line per request with no personal data.
4. Write the results into a short table in this document, or into `docs/verification.md`.
5. Log anything that fails as a fix task, and re-run that criterion after the fix.

**Done when**
- [ ] Every AC and IAC criterion is marked pass, or has a linked fix task
- [ ] No personal data or secrets appear in CloudWatch logs

---

### P10 · Review and hardening

**Goal.** No Critical or High findings left before this is shown or shared.

**Depends on.** P9

**Steps**
1. Run the `code-reviewer` agent across the whole diff, then fix everything Critical and High.
2. Run the `iac-reviewer` agent once more on the final Terraform.
3. Run a cross-model review of the diff with the `cross-model-reviewer` agent, and compare its findings with Claude's.
4. Check the repository for secrets before pushing: `git diff --stat`, and confirm no `.env`, `*.tfvars`, or state files are staged.
5. Review each README by following its steps on a clean checkout.
6. Decide what comes next: authentication (R1), CloudFront with HTTPS (R2), and CI/CD.

**Done when**
- [ ] No Critical or High findings remain open
- [ ] No secret or state file has ever been committed
- [ ] Each README works when followed from scratch
- [ ] The next steps are written down in the proposal

---

## 5. Tracking

| Phase | Owner | Status | Notes |
|---|---|---|---|
| P0 Foundations | | done | Committed? |
| P1 Documents and review | | not started | Answer the 4 open questions first |
| P2 API design | | not started | |
| P3 DB and migration | | not started | First README lands here |
| P4 Java API | | not started | Needs JDK 21 and Maven |
| P5 Angular UI | | not started | Needs Node 22 |
| P6 Terraform network and DB | | not started | Can run alongside P2–P5 |
| P7 Terraform API and frontend | | not started | Needs the JAR from P4 |
| P8 Deploy | | not started | A human applies |
| P9 Verification | | not started | |
| P10 Review | | not started | |

## 6. Decisions log

| Date | Decision | Where |
|---|---|---|
| 2026-09-22 | Region is `us-east-1` | proposal §5.4 |
| 2026-09-22 | READMEs are written during implementation, not up front | CLAUDE.md, proposal §13 |
| 2026-09-22 | Every behaviour needs a positive **and** a negative unit test | plan §1, proposal §5.5 |
| | Plain Java or Spring Boot | open, proposal §14.1 |
| | Authentication for the demo | open, proposal §14.5 |

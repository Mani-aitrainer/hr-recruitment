# Proposal: HR Recruitment · Job Posting module

| | |
|---|---|
| Status | Draft, for review |
| Author | Mani |
| Date | 2026-09-22 |
| Scope | Job posting screen only (create, edit, publish, close, list, view) |
| Related documents | `docs/implementation-plan.md` (phases and gates), `docs/specs/job-posting.spec.md`, `docs/specs/infrastructure.spec.md`, `docs/api-design.md` (written in phase P2) |

---

## 1. Summary

Build the first slice of an HR Recruitment system: a **job posting screen** where recruiters create, edit, publish, and close job posts.
It is built as a **monorepo** so one coding agent (Claude Code) can see and change every layer in one task:

| Folder | Layer | Technology |
|---|---|---|
| `ui/` | Front end | Angular 22 (`@angular/cli@22.1.8`), standalone components, signals |
| `api/` | Back end | Java 21 on AWS Lambda, exposed with a Lambda Function URL |
| `db/` | Database | PostgreSQL on Amazon RDS (`db.t4g.micro`, 20 GB gp3), versioned SQL migrations |
| `iac/` | Infrastructure | Terraform: VPC, RDS, Lambda, IAM, Secrets Manager, S3 static hosting |

The goal is a working, deployed, end-to-end feature. The project also serves as a reference for building software with a spec-first, agent-assisted process.

## 2. Problem and goal

**Problem.** Recruiters keep job openings in spreadsheets and emails. There is no single, validated record of which roles are open, what they require, or when they close.

**Goal.** A single screen where a recruiter can:

1. See all job posts, with search, a status filter, and paging
2. Create a job post as a **draft**, with validation
3. Edit a draft
4. **Publish** a draft. After that the post can no longer be edited.
5. **Close** a published post
6. Delete a draft (soft delete)

**Success criteria**
- Every acceptance criterion in `docs/specs/job-posting.spec.md` passes as an automated test, with both positive and negative cases (§5.5)
- The app is deployed to AWS by `terraform apply` alone, with no manual console steps apart from the one-time DB migration invoke
- Monthly running cost of the dev environment stays under about US$40 (see §9)

## 3. Scope

### In scope
- Job posting CRUD and the status lifecycle `DRAFT → PUBLISHED → CLOSED`
- List screen, create/edit form, and detail view
- REST API `/api/v1/job-postings`
- PostgreSQL schema and migrations
- Terraform for a single `dev` environment
- Unit tests for every feature, positive and negative (see §5.5), and a code review agent run

### Out of scope
- Candidates, applications, interviews, and offers
- Authentication and authorization (no login in this version; see risk R1)
- Multi-environment promotion (test/prod) and CI/CD pipelines
- CloudFront, a custom domain, or HTTPS on the UI (see risk R2)
- Email notifications and job-board integrations
- Internationalisation beyond keeping labels in one i18n file

## 4. Architecture

```
                         AWS account (all resources created by Terraform in iac/)
                        ┌─────────────────────────────────────────────────────────────────┐
┌──────────┐  HTTP     │  ┌─────────────┐                ┌─ VPC ─────────────────────────┐ │
│ Browser  │──────────▶│  │ S3 bucket   │                │   private subnets (2 AZs)     │ │
│recruiter │           │  │ Angular app │                │                               │ │
└────┬─────┘           │  └─────────────┘                │ ┌──────────────┐  5432  ┌───────────┐
     │   HTTPS + CORS  │  ┌──────────────┐               │ │ Java 21      │──────▶│PostgreSQL │
     └────────────────▶│  │Lambda        │──────────────▶│ │ API Lambda   │       │ RDS       │
                       │  │Function URL  │               │ └──────┬───────┘       │t4g.micro  │
                       │  └──────────────┘               │        │ VPC endpoint  └───────────┘
                       │                                 │ ┌──────▼─────────┐              │
                       │  IAM roles: Lambda exec + VPC   │ │Secrets Manager │ (DB creds)   │
                       │                                 │ └────────────────┘              │
                       │                                 └─────────────────────────────────┘
                        └─────────────────────────────────────────────────────────────────┘
```

**Request flow**
1. The browser loads the Angular app from the S3 static website endpoint.
2. The app calls the Lambda Function URL over HTTPS. CORS allows only the S3 website origin.
3. The Lambda runs inside the VPC's private subnets. On a cold start it reads DB credentials from Secrets Manager through an interface VPC endpoint.
4. The Lambda queries PostgreSQL. The RDS security group accepts port 5432 **only** from the Lambda security group.

**Why no NAT gateway:** the Lambda only needs to reach RDS and Secrets Manager. A Secrets Manager VPC endpoint (about $15/month across 2 AZs) is cheaper than a NAT gateway (about $33/month plus data charges), and it keeps the Lambda without any internet egress.

## 5. Technology decisions

### 5.1 UI: Angular 22
| Decision | Choice | Reason |
|---|---|---|
| Version | Angular 22, `@angular/cli@22.1.8` | Specified for the project |
| Components | Standalone, `OnPush` by default | Standard Angular approach since v17; no NgModules |
| State | Signals (`signal`, `computed`, `resource`/`httpResource` for reads) | Simpler than RxJS stores for a single feature |
| Forms | Reactive forms; validators taken from the spec's field rules | Validation rules stay in one place |
| Templates | Built-in control flow (`@if`, `@for`) | Current Angular syntax |
| Styling | SCSS, no UI component library for v1 | Keeps the bundle small; can add Angular Material later |
| Tests | The runner that `ng new` generates for v22 (Vitest by default in recent versions) | Keep the CLI default and don't add a custom runner |
| API URL | `environment.ts` / `environment.development.ts`, written by Terraform output at deploy time | No hard-coded URLs |

### 5.2 API: Java 21 on Lambda
| Decision | Choice | Reason |
|---|---|---|
| Runtime | AWS Lambda `java21`, arm64 | Specified; arm64 is about 20% cheaper |
| Entry point | Lambda Function URL (payload format 2.0) | No API Gateway needed for one service |
| Framework | **Recommended: plain Java with a small router** (Jackson, Hibernate Validator, JDBC with HikariCP pool size 2) | Fast cold start (about 1–2 s, lower with SnapStart), small JAR, and nothing hidden from the agent |
| Alternative | Spring Boot 3 with `aws-serverless-java-container-springboot3` and SnapStart | Familiar to Java teams, but a heavier JAR and slower cold start without SnapStart |
| Layering | Handler → Router → Controller → Service → Repository | Enforced by `.claude/rules/java-api.md` |
| Errors | RFC 7807 `application/problem+json` | Standard and machine-readable |
| Build | Maven, shaded JAR (`api/target/api.jar`) | Terraform uploads the JAR |
| Tests | JUnit 5, Mockito, Testcontainers PostgreSQL for repository tests | Repository tests run against real PostgreSQL, not mocks |

> **Decision needed:** plain Java (recommended) or Spring Boot. The rest of this proposal assumes plain Java.

### 5.3 Database: PostgreSQL on RDS
| Decision | Choice | Reason |
|---|---|---|
| Engine | PostgreSQL 17 | Current major version supported by RDS |
| Instance | `db.t4g.micro`, 20 GB gp3, single-AZ, storage encrypted | Minimal hardware spec for dev |
| Access | Private subnets, `publicly_accessible = false` | The DB is never exposed to the internet |
| Credentials | `manage_master_user_password = true` (RDS-managed secret in Secrets Manager) | No passwords in Terraform state or code |
| Migrations | Versioned SQL files, `db/migrations/V{n}__{desc}.sql`, applied by Flyway | Repeatable and ordered |
| Running migrations | A second handler in the API JAR (`MigrationHandler`), deployed as a `db-migrate` Lambda in the same VPC and invoked once with `aws lambda invoke` | The DB is private, so migrations must run from inside the VPC. This avoids a bastion host. |
| Deletes | Soft delete (`deleted_at`) | Keeps an audit trail |

### 5.4 Infrastructure: Terraform
| Decision | Choice | Reason |
|---|---|---|
| Tool | Terraform ≥ 1.10 (1.16 installed locally), AWS provider ~> 6.x | Specified |
| Layout | `iac/modules/{network,database,api,frontend}` + `iac/envs/dev` | Modules can be reused for test/prod later |
| State | S3 backend with native lock file (`use_lockfile = true`) | No DynamoDB table needed on Terraform ≥ 1.10 |
| Region | `us-east-1` (N. Virginia), set by a variable | Default region for the account; lowest list prices and every service available |
| Tags | `project=hr-recruitment`, `env`, `owner`, `managed-by=terraform` | Cost tracking |
| Frontend hosting | S3 static website hosting, public-read policy on this bucket only | Specified; CloudFront later (R2) |

### 5.5 Testing: positive and negative cases

**Instruction: every unit of behaviour gets both a positive and a negative test.** A feature is not done when only the happy path is proven. Write them together, in the same task as the code.

| Test type | What it proves | Example |
|---|---|---|
| **Positive** | Valid input produces the expected result and state change | `POST` with a valid body returns `201` with status `DRAFT` |
| **Negative: validation** | Each field rule rejects bad input with a clear error | `title` of 4 chars → `400` with an error on `title` |
| **Negative: business rule** | Forbidden actions are refused | Editing a `PUBLISHED` post → `409 invalid-status-transition` |
| **Negative: missing or stale** | Absent or concurrent data is handled | Unknown id → `404`; stale `version` → `409 version-conflict` |
| **Negative: boundary** | Limits hold on both sides | `title` at 4 / 5 / 100 / 101 chars; `openings` 0 / 1 / 100 / 101 |

High-level coverage expected per layer:

| Layer | Positive | Negative |
|---|---|---|
| Java service | One test per rule and per allowed status transition | Every rejected transition, every validation failure, not-found, version conflict |
| Java controller | Each endpoint returns the documented success status and body | Malformed JSON, unknown path, bad query parameters, the documented `400`/`404`/`409` responses |
| Java repository (Testcontainers) | Insert, read, update, page, filter, and search work | Constraint violations are rejected, soft-deleted rows stay hidden, a stale version updates no rows |
| Angular service | Each call sends the right request and maps the response | Error responses surface as typed failures rather than crashes |
| Angular components | Valid form submits; the list and detail render | Invalid form blocks submission and shows the message, actions disabled by status, error and empty states render |
| Database | A valid row inserts | Every `CHECK` constraint rejects an invalid row |

Name tests after what they prove, and after the acceptance criterion where there is one, for example `AC-6 rejects edit of published post with 409`. Keep them deterministic: no reliance on today's date beyond the spec's rules, and no shared state between tests.

## 6. Monorepo layout

```
hr-recruitment/
├── CLAUDE.md                     # whole-system overview for the agent
├── .mcp.json                     # shared MCP servers
├── .claude/
│   ├── settings.json             # team permissions
│   ├── rules/                    # always-on standards, scoped by path
│   ├── skills/                   # how-to guides, loaded on demand
│   └── agents/                   # reviewer and designer subagents
├── docs/
│   ├── proposal.md               # this document
│   ├── implementation-plan.md    # next
│   ├── api-design.md             # produced by the api-designer agent
│   └── specs/
│       ├── job-posting.spec.md
│       └── infrastructure.spec.md
├── ui/     (Angular 22)          + CLAUDE.md
├── api/    (Java 21 Lambda)      + CLAUDE.md
├── db/     (SQL migrations)      + CLAUDE.md
└── iac/    (Terraform)           + CLAUDE.md
```

## 7. Data model (summary)

A single table `job_posting`. The spec is the source of truth for the full field rules.

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK | `gen_random_uuid()` |
| `title` | `varchar(100)` | 5–100 chars |
| `department` | `varchar(40)` | from a fixed list, enforced with a CHECK constraint |
| `location` | `varchar(100)` | |
| `employment_type` | `varchar(20)` | `FULL_TIME`, `PART_TIME`, `CONTRACT` |
| `experience_min` / `experience_max` | `smallint` | 0–40, min ≤ max |
| `skills` | `text[]` | 1–15 tags |
| `description` | `text` | 50–5000 chars |
| `openings` | `smallint` | 1–100 |
| `closing_date` | `date` | future date when created or published |
| `status` | `varchar(12)` | `DRAFT`, `PUBLISHED`, `CLOSED` |
| `created_at`, `updated_at`, `published_at`, `closed_at`, `deleted_at` | `timestamptz` | audit fields |
| `version` | `integer` | optimistic locking |

## 8. API (summary)

Base path `/api/v1/job-postings`. See the spec for full request, response, and error details.

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | List, with `?status=&q=&page=&size=` |
| GET | `/{id}` | Detail |
| POST | `/` | Create, returns `201` with status `DRAFT` |
| PUT | `/{id}` | Update (DRAFT only) |
| PATCH | `/{id}/status` | Publish or close |
| DELETE | `/{id}` | Soft delete (DRAFT only) |

Errors: `400` validation, `404` not found, `409` invalid status transition or version conflict.

## 9. Security

- **No secrets in Git.** DB credentials live only in Secrets Manager. `.env`, `*.tfvars`, and Terraform state are git-ignored, and Claude is denied read access to `.env`.
- **Least privilege IAM.** The Lambda role gets `AWSLambdaVPCAccessExecutionRole` plus `secretsmanager:GetSecretValue` on the one RDS secret ARN.
- **Network isolation.** RDS accepts connections only from the Lambda security group. There is no NAT and no public DB.
- **Input validation.** Every request DTO is validated on the server. The UI validation is for usability only and is not trusted.
- **SQL.** Prepared statements only (see `.claude/rules/java-api.md`).
- **Abuse limits.** Reserved concurrency of 5 on the API Lambda caps cost if the public URL is abused.
- **Logging.** No request bodies, secrets, or personal data in logs. Each log line carries the `requestId`.

## 10. Cost estimate (dev, per month, approximate)

| Item | Estimate |
|---|---|
| RDS `db.t4g.micro` single-AZ + 20 GB gp3 | ~$14 |
| Secrets Manager: 1 secret + interface endpoint in 2 AZs | ~$16 |
| Lambda, S3, CloudWatch Logs (low traffic) | < $2 |
| **Total** | **~$32/month** |

These are approximate list prices. Confirm them with the AWS Pricing Calculator for `us-east-1`. Run `terraform destroy` when the environment is not in use.

## 11. Delivery process and phases

Every change follows **proposal → implementation plan → spec → cross-model review → implementation**. Review findings go back into the documents before any code is written.

| # | Phase | Output | Claude components used |
|---|---|---|---|
| 1 | Repo and monorepo structure | `ui/ api/ db/ iac/`, CLAUDE.md files | root and folder CLAUDE.md |
| 2 | Agent harness | rules, skills, agents, settings, MCP | `.claude/` |
| 3 | Proposal → plan → spec, cross-model review | `docs/*` updated with findings | `cross-model-reviewer` (user level) |
| 4 | API design | `docs/api-design.md` | `api-designer` agent |
| 5 | Angular job posting screens | `ui/src/app/features/job-posting/` **+ `ui/README.md` + root `README.md`** | `job-posting-ui` skill, `angular-ui` rule |
| 6 | Java API Lambda | `api/` handlers, services, repositories, tests **+ `api/README.md`** | `java-lambda-endpoint` skill, `java-api` rule |
| 7 | Terraform: network and PostgreSQL | `iac/modules/network`, `iac/modules/database` **+ `iac/README.md`** | `terraform-change` skill, `terraform` rule |
| 8 | Terraform: IAM, Lambda, Function URL, S3 | `iac/modules/api`, `iac/modules/frontend`, updated `iac/README.md` | same, plus `iac-reviewer` agent |
| 9 | DB migration from the API design | `db/migrations/V1__create_job_posting.sql`, run via `db-migrate` Lambda **+ `db/README.md`** | `db-migration` skill, `sql-migrations` rule |
| 10 | Review everything | findings table, fixes | `code-reviewer` agent |

## 12. Risks and mitigations

| ID | Risk | Mitigation |
|---|---|---|
| R1 | The Function URL is public with no auth | Demo data only; reserved concurrency cap; CORS restricted. Add Cognito/IAM auth in the next version. |
| R2 | The S3 website endpoint serves HTTP only | Acceptable for dev. Put CloudFront with OAC and HTTPS in front before any real use. |
| R3 | Java cold starts in a VPC | Plain Java, small JAR, arm64; enable SnapStart on a published alias if p95 > 3 s |
| R4 | Too many DB connections from Lambda | HikariCP pool size 2 plus reserved concurrency 5 (at most 10 connections) |
| R5 | Local toolchain mismatch (this machine has Java 11 and Node 16) | Install JDK 21, Maven 3.9+, and Node.js 22.12+ or 24 LTS before phases 5–6 |
| R6 | The agent builds features outside the scope | Out-of-scope list in the spec; the code-reviewer checks spec compliance |

## 13. Prerequisites and README files

The prerequisites below are for the whole project. **Per-project prerequisites and run instructions are written as README files during implementation, not now.** Each project's first implementation change (phases 5–9) must also create that folder's `README.md` with:

1. Prerequisites: each tool, its minimum version, and a command to check it
2. First-time setup, including scaffolding and configuration
3. Numbered, step-by-step run instructions with the expected result of each step
4. Test and build commands
5. Troubleshooting for common failures

A root `README.md` is added with the first project, and links the four folder READMEs together with the local and AWS run order.

| Tool | Version | Status on this machine |
|---|---|---|
| Node.js | 22.12+ or 24 LTS (check Angular 22's compatibility table) | 16.14 installed → **upgrade** |
| Angular CLI | 22.1.8 (`npx @angular/cli@22.1.8 new ...`) | — |
| JDK | 21 | 11 installed → **install 21** |
| Maven | 3.9+ | not installed → **install** |
| Terraform | ≥ 1.10 | 1.16.3 ✓ |
| AWS CLI | v2, with a profile for the target account | verify |

## 14. Open questions

1. Plain Java or Spring Boot for the Lambda (§5.2)?
2. ~~Which region?~~ **Decided: `us-east-1`.**
3. Should departments be a fixed list (current plan) or a managed table?
4. Should recruiters be able to reopen a CLOSED post? (Current plan: no.)
5. Is it acceptable to have no authentication for the dev demo (R1)?

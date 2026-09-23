# Spec: Candidate capture · HR Recruitment

Version 0.1 · Source proposal: `docs/proposal.md` · Plan: `docs/candidate_implementation_plan.md`

## Goal
Recruiters capture a candidate's basic and professional information as a standalone talent pool record.

## Out of scope
Applications, linking a candidate to a job posting, interviews, offers, resume or file upload, salary and CTC fields, date of birth, gender, authentication, notifications, duplicate detection, a candidate list screen, a candidate detail screen, archiving from the UI.

### Known limits in v1

These follow from the scope above and are deliberate, not omissions:

- **`ARCHIVED` is unreachable.** The column exists and is constrained, but the transition needs `PATCH /{id}/status` and a detail screen, neither of which is in v1. Every candidate stays `ACTIVE`.
- **There is no browse path.** With no list screen, an existing candidate is reachable only by direct URL (`/candidates/:id/edit`).
- **Duplicates are allowed.** No uniqueness rule on `email` or `phone`, so the only `409` in this spec is `version-conflict`.

## Roles
| Role | Can |
|---|---|
| Recruiter | Everything in this spec. There is no login in v1, so every caller is treated as a recruiter. |

## User stories
- **US-1** As a recruiter, I want to capture a candidate's name and contact details, so the person is on record.
- **US-2** As a recruiter, I want to capture their current role, experience, qualification, and skills, so I can judge fit later.
- **US-3** As a recruiter, I want to correct a candidate I just saved, so I can fix a typo without creating a second record.
- **US-4** As a recruiter, I want to be told exactly which field is wrong before anything is sent, so I am not guessing.

## Fields and validation

The same rules apply in the UI (for usability) and the API (enforced).

### Basic information

| Field | Type | Rule |
|---|---|---|
| `fullName` | string | required, trimmed, 2–100 chars |
| `email` | string | required, valid email address, ≤ 255 chars, trimmed and stored lowercase |
| `phone` | string | required, 8–20 chars, digits with optional leading `+`, spaces, and hyphens |
| `location` | string | required, 2–100 chars |

### Professional information

| Field | Type | Rule |
|---|---|---|
| `currentEmployer` | string | optional, 2–100 chars when present |
| `currentTitle` | string | optional, 2–100 chars when present |
| `totalExperienceYears` | integer | required, 0–40 |
| `noticePeriodDays` | integer | optional, 0–180 |
| `highestQualification` | enum | required, one of `HIGH_SCHOOL`, `DIPLOMA`, `BACHELORS`, `MASTERS`, `DOCTORATE` |
| `skills` | string[] | 1–15 items, each 1–30 chars, case-insensitive unique |
| `summary` | string | optional, ≤ 2000 chars (plain text, no HTML) |

### Server-set

| Field | Type | Rule |
|---|---|---|
| `status` | enum | read-only, `ACTIVE` \| `ARCHIVED`, defaults to `ACTIVE` |
| `id`, `createdAt`, `updatedAt`, `archivedAt`, `version` | — | read-only, set by the server |

`skills` follows exactly the same rule as `job_posting.skills`, so the chip input component and its validator are shared, not copied.

## Status lifecycle

```
          create                  archive (not in v1)
  (none) ───────▶ ACTIVE ─────────────────────────▶ ARCHIVED
                   │  ▲ edit (PUT)                  (final)
                   │  └──┘
                   └── delete (soft) ──▶ (hidden)
```

| From | Action | To | Allowed |
|---|---|---|---|
| ACTIVE | PUT (edit) | ACTIVE | yes |
| ACTIVE | archive | ARCHIVED | not exposed in v1 — no endpoint, no screen |
| ARCHIVED | any change | — | no → `409 invalid-status-transition` |

The `ARCHIVED` state and its `409` rule are specified now so the database constraint and the service guard are written once. No v1 request can reach `ARCHIVED`.

## API contract

Base URL: `{FUNCTION_URL}/api/v1/candidates` · JSON · UTF-8 · dates in ISO-8601 UTC.

| Method | Path | Request | Success | Errors |
|---|---|---|---|---|
| POST | `/` | CandidateRequest | `201` Candidate, `Location` header | `400` |
| GET | `/{id}` | — | `200` Candidate | `404` |
| PUT | `/{id}` | CandidateRequest + `version` | `200` Candidate | `400`, `404`, `409` |

`GET /`, `PATCH /{id}/status`, and `DELETE /{id}` are **not** in v1. They arrive with the list and detail screens.

**Error shape (RFC 7807, `application/problem+json`)** — identical to `docs/specs/job-posting.spec.md`.
```json
{
  "type": "https://hr-recruitment/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/v1/candidates",
  "requestId": "c0a8…",
  "errors": [ { "field": "totalExperienceYears", "message": "must be between 0 and 40" } ]
}
```

`409` types: `version-conflict` (the only one reachable in v1) and `invalid-status-transition`. Soft-deleted candidates return `404`.

## Screens

### 1. Capture form (`/candidates/new`, `/candidates/:id/edit`)
- Two labelled sections, **Basic information** and **Professional information**, with the fields in the order of the tables above
- Inline errors shown after a field is touched or on submit
- Skills as a chip input (Enter or comma adds a chip), rejecting a case-insensitive duplicate with a message
- A live character counter for `summary`
- `highestQualification` as a select; optional fields marked "(optional)" in the label
- **New mode:** Save creates the candidate, then shows a success confirmation naming the candidate and resets the form for the next entry
- **Edit mode:** the route loads the candidate by id, prefills the form, and Save sends `PUT` with the loaded `version`. An unknown id shows a not-found message, not an empty form.
- Cancel clears the form in new mode and returns to the loaded values in edit mode
- Warn about unsaved changes when navigating away
- Loading state while the edit route fetches, and an error state with a Retry button

There is no list screen and no detail screen in v1.

## Acceptance criteria

- **CAND-1** *Given* a valid form, *when* the recruiter saves, *then* the API returns `201`, `status` is `ACTIVE`, `version` is 1, and the UI shows a success confirmation and an empty form.
- **CAND-2** *Given* a `fullName` of 1 character, *when* the recruiter saves, *then* the UI shows "Full name must be 2–100 characters" and no request is sent.
- **CAND-3** *Given* an `email` of "mani@", *when* the recruiter saves, *then* the UI shows an email format error and no request is sent.
- **CAND-4** *Given* `totalExperienceYears` of 41, *when* the request reaches the API, *then* the API returns `400` with an error on `totalExperienceYears`, and 40 is accepted.
- **CAND-5** *Given* skills `["Java", "java"]`, *when* the request reaches the API, *then* the API returns `400` with an error on `skills`, and 15 distinct skills are accepted while 16 are rejected.
- **CAND-6** *Given* an existing candidate, *when* the recruiter opens `/candidates/:id/edit` and saves a changed `currentTitle`, *then* the API returns `200`, the value is updated, and `version` increases by 1.
- **CAND-7** *Given* two users edit the same candidate, *when* the second saves with a stale `version`, *then* the API returns `409` with type `version-conflict` and the UI asks the user to reload.
- **CAND-8** *Given* an id that does not exist, *when* the client sends `GET /{id}` or `PUT /{id}`, *then* the API returns `404` and the edit route shows a not-found message.

## Documentation deliverable

Implementing any part of this spec also means updating the README for the folder you changed (`ui/README.md`, `api/README.md`, `db/README.md`). Those files already exist from the job posting phases, so add the candidate commands, environment variables, and troubleshooting entries to them rather than starting new files. Write only steps you have actually run.

## Non-functional requirements
- **Performance:** p95 API latency under 300 ms warm and under 3 s cold. The form is interactive within 2 s on 4G.
- **Accessibility:** WCAG 2.2 AA. Every input has a label, optional fields say so, errors are linked with `aria-describedby`, the two sections use `fieldset`/`legend`, and the whole form including the chip input is usable with the keyboard alone.
- **Security:** see proposal §9 and risk R7. This module stores personal data on an unauthenticated public Function URL, so **only synthetic candidate data may be used in seed files, tests, fixtures, and demos**. The server validates all input.
- **Privacy in logs:** `fullName`, `email`, `phone`, `summary`, and `currentEmployer` must never appear in any log line, error message returned to the client, or exception stack trace. Validation errors name the field, never the value.
- **Observability:** one structured JSON log line per request with `requestId`, method, path, status, and duration.

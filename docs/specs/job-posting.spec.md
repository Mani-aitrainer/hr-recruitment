# Spec: Job posting · HR Recruitment

Version 0.1 · Source proposal: `docs/proposal.md`

## Goal
Recruiters create, edit, publish, and close job posts.

## Out of scope
Applications, candidates, interviews, offers, authentication, notifications, file uploads, reopening a closed post.

## Roles
| Role | Can |
|---|---|
| Recruiter | Everything in this spec. There is no login in v1, so every caller is treated as a recruiter. |

## User stories
- **US-1** As a recruiter, I want to see all job posts with search and a status filter, so I can find a role quickly.
- **US-2** As a recruiter, I want to create a job post as a draft, so I can prepare it before it goes live.
- **US-3** As a recruiter, I want to edit a draft, so I can fix mistakes before publishing.
- **US-4** As a recruiter, I want to publish a draft, so the role is officially open.
- **US-5** As a recruiter, I want to close a published post, so no one treats a filled role as open.
- **US-6** As a recruiter, I want to delete a draft I no longer need.

## Fields and validation

The same rules apply in the UI (for usability) and the API (enforced).

| Field | Type | Rule |
|---|---|---|
| `title` | string | required, trimmed, 5–100 chars |
| `department` | enum | required, one of `ENGINEERING`, `HUMAN_RESOURCES`, `FINANCE`, `SALES`, `MARKETING`, `OPERATIONS` |
| `location` | string | required, 2–100 chars |
| `employmentType` | enum | required, `FULL_TIME` \| `PART_TIME` \| `CONTRACT` |
| `experienceMin` | integer | required, 0–40 |
| `experienceMax` | integer | required, 0–40, ≥ `experienceMin` |
| `skills` | string[] | 1–15 items, each 1–30 chars, case-insensitive unique |
| `description` | string | required, 50–5000 chars (plain text, no HTML) |
| `openings` | integer | required, 1–100 |
| `closingDate` | date (`YYYY-MM-DD`) | required, later than today (UTC) when the post is created, updated, or published |
| `status` | enum | read-only, `DRAFT` \| `PUBLISHED` \| `CLOSED` |
| `id`, `createdAt`, `updatedAt`, `publishedAt`, `closedAt`, `version` | — | read-only, set by the server |

## Status lifecycle

```
          create                publish                 close
  (none) ───────▶ DRAFT ───────────────▶ PUBLISHED ───────────────▶ CLOSED
                   │  ▲ edit (PUT)                                  (final)
                   │  └──┘
                   └── delete (soft) ──▶ (hidden)
```

| From | Action | To | Allowed |
|---|---|---|---|
| DRAFT | PUT (edit) | DRAFT | yes |
| DRAFT | publish | PUBLISHED | yes, only if every field is valid and `closingDate` > today |
| DRAFT | delete | (soft deleted) | yes |
| PUBLISHED | close | CLOSED | yes |
| PUBLISHED | edit / delete / publish | — | no → `409` |
| CLOSED | any change | — | no → `409` |

## API contract

Base URL: `{FUNCTION_URL}/api/v1/job-postings` · JSON · UTF-8 · dates in ISO-8601 UTC.

| Method | Path | Request | Success | Errors |
|---|---|---|---|---|
| GET | `/` | query: `status?`, `q?` (matches title, location, skills), `page` (default 0), `size` (default 20, max 100), `sort` (default `updatedAt,desc`) | `200` Page | `400` |
| GET | `/{id}` | — | `200` JobPosting | `404` |
| POST | `/` | JobPostingRequest | `201` JobPosting, `Location` header | `400` |
| PUT | `/{id}` | JobPostingRequest + `version` | `200` JobPosting | `400`, `404`, `409` |
| PATCH | `/{id}/status` | `{ "action": "PUBLISH" \| "CLOSE", "version": n }` | `200` JobPosting | `400`, `404`, `409` |
| DELETE | `/{id}` | query: `version` | `204` | `404`, `409` |

**Page shape**
```json
{ "items": [ /* JobPosting */ ], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3 }
```

**Error shape (RFC 7807, `application/problem+json`)**
```json
{
  "type": "https://hr-recruitment/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/v1/job-postings",
  "requestId": "c0a8…",
  "errors": [ { "field": "title", "message": "must be 5-100 characters" } ]
}
```

`409` types: `invalid-status-transition` and `version-conflict`. Soft-deleted posts return `404`.

## Screens

### 1. Job post list (`/job-postings`)
- Table columns: Title, Department, Location, Type, Openings, Closing date, Status (badge), Updated
- Search box (debounced 300 ms) and a status filter (All / Draft / Published / Closed)
- Paging: 20 per page. Filters are kept in the URL query string.
- "New job post" button
- Empty state, loading state, and error state with a Retry button

### 2. Create / edit form (`/job-postings/new`, `/job-postings/:id/edit`)
- All fields from the table above, with inline errors shown after a field is touched or on submit
- Skills as a chip input (Enter or comma adds a chip)
- A live character counter for `description`
- Save (stays DRAFT) and Cancel. The edit route redirects to detail if the post is not DRAFT.
- Warn about unsaved changes when navigating away

### 3. Detail view (`/job-postings/:id`)
- Read-only view of all fields plus the status history timestamps
- Actions by status: DRAFT → Edit, Publish, Delete · PUBLISHED → Close · CLOSED → none
- Publish, Close, and Delete ask for confirmation

## Acceptance criteria

- **AC-1** *Given* no posts exist, *when* the recruiter opens the list, *then* an empty state with a "New job post" button is shown.
- **AC-2** *Given* a valid form, *when* the recruiter saves, *then* the API returns `201`, the status is `DRAFT`, and the recruiter lands on the detail view.
- **AC-3** *Given* a title of 4 characters, *when* the recruiter saves, *then* the UI shows "Title must be 5–100 characters" and no request is sent.
- **AC-4** *Given* `experienceMin` 8 and `experienceMax` 5, *when* the request reaches the API, *then* the API returns `400` with an error on `experienceMax`.
- **AC-5** *Given* a DRAFT post with valid fields, *when* the recruiter clicks Publish and confirms, *then* the status becomes `PUBLISHED`, `publishedAt` is set, and the Edit button is no longer shown.
- **AC-6** *Given* a PUBLISHED post, *when* a client sends PUT, *then* the API returns `409` with type `invalid-status-transition`.
- **AC-7** *Given* a PUBLISHED post, *when* the recruiter closes it, *then* the status becomes `CLOSED` and no actions remain.
- **AC-8** *Given* two users edit the same draft, *when* the second saves with a stale `version`, *then* the API returns `409` with type `version-conflict` and the UI asks the user to reload.
- **AC-9** *Given* a DRAFT post, *when* the recruiter deletes it, *then* it no longer appears in the list and `GET /{id}` returns `404`.
- **AC-10** *Given* 45 posts, *when* the list loads with `size=20`, *then* 3 pages are shown and the status filter narrows the total.
- **AC-11** *Given* a search term "spring", *when* entered, *then* only posts whose title, location, or skills contain it (case-insensitive) are listed.

## Documentation deliverable

Implementing any part of this spec also means writing the README for the folder you changed (`ui/README.md`, `api/README.md`, `db/README.md`), and a root `README.md` with the first project. Each README covers: prerequisites with versions and check commands, first-time setup, numbered run instructions with the expected result of each step, test and build commands, and troubleshooting. Do not write these before the code exists; write them from the commands you actually ran.

## Non-functional requirements
- **Performance:** p95 API latency under 300 ms warm and under 3 s cold. The list page is interactive within 2 s on 4G.
- **Accessibility:** WCAG 2.2 AA. Every input has a label, errors are linked with `aria-describedby`, and the UI is fully usable with the keyboard.
- **Security:** see proposal §9. The server validates all input, and no personal data is written to logs.
- **Observability:** one structured JSON log line per request with `requestId`, method, path, status, and duration.

---
name: job-posting-ui
description: Builds or changes the Angular 22 screens for the HR job posting feature (list, create/edit form, detail) in ui/src/app/features/job-posting. Use when creating, modifying, or testing job posting UI components, the job posting service, or its form validation.
---
# Job posting UI

## Steps
1. Read `docs/specs/job-posting.spec.md`: the field table, the screens, and the acceptance criteria. Read `docs/api-design.md` if it exists.
2. If `ui/package.json` does not exist, stop and tell the user to scaffold with `npx @angular/cli@22.1.8 new ...` (see `ui/CLAUDE.md`).
3. Model: `data/job-posting.model.ts`, with types that match the API JSON exactly (`JobPosting`, `JobPostingRequest`, `Page<T>`, `ProblemDetail`, and the enums as string unions).
4. Service: `data/job-posting.service.ts`, one method per endpoint, using `HttpClient` and `environment.apiBaseUrl`.
5. Components, one standalone folder each:
   - `list/`: table, debounced search, status filter, and paging kept in the query string, plus empty, loading, and error states
   - `form/`: typed reactive form, validators copied from the spec field table, a skills chip input, a description counter, and an unsaved-changes guard
   - `detail/`: read-only view, actions based on status, and a confirmation before Publish, Close, or Delete
6. Routes: lazy `job-posting.routes.ts`, registered in `app.routes.ts` under `job-postings`.
7. Map a `400` problem+json `errors[]` onto form controls. On `409 version-conflict`, show a reload prompt.
8. Tests: one spec per component and service. Name each test after its AC, for example `it('AC-3 shows title length error and sends no request')`.
9. Run `npm run lint`, `npm test -- --watch=false`, and `npm run build`. Fix any failures before reporting.

## Conventions
Follow `.claude/rules/angular-ui.md`. Reuse `shared/` components before creating new ones.

## Done when
- `ui/README.md` exists (create it in this task if missing) with: prerequisites and versions, the scaffold step, numbered run steps with the expected result of each, test and build commands, and troubleshooting. Add the root `README.md` if this is the first project implemented.
- Every component and the service have at least one positive and one negative test (invalid form, boundary value, server `400`/`409`/`404`, empty and error states)
- Lint, tests, and build pass
- Every UI-facing acceptance criterion (AC-1, 2, 3, 5, 7, 8, 9, 10, 11) has a test
- No hard-coded API URL or label text

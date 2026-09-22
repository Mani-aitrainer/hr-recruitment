# UI · Angular job posting app

Angular 22 (`@angular/cli@22.1.8`), standalone components, signals, SCSS. Built to static files and hosted on S3 website hosting.

## Setup (not scaffolded yet)
Requires Node.js 22.12+ or 24 LTS. Run from the repo root:
```
npx @angular/cli@22.1.8 new ui --directory ui --style scss --routing --ssr false --skip-git
```

## Commands
`npm ci` · `npm start` (http://localhost:4200) · `npm test -- --watch=false` · `npm run lint` · `npm run build` → `dist/ui/browser/`

## Structure
```
src/app/core/                      http interceptors, error handling, config
src/app/shared/                    reusable UI (status badge, chip input, confirm dialog)
src/app/features/job-posting/
  job-posting.routes.ts            lazy-loaded routes
  data/job-posting.model.ts        types matching the API contract
  data/job-posting.service.ts      the only place that calls /api/v1/job-postings
  list/  form/  detail/            one standalone component per screen
src/environments/                  apiBaseUrl (Function URL) lives here only
src/i18n/en.json                   all user-facing labels
```

## Conventions
- Standalone components with `ChangeDetectionStrategy.OnPush`, signals for state, and `@if`/`@for` control flow
- Reactive forms. Validators mirror the spec's field table exactly.
- Keep list filters in the URL query string
- Errors: parse `application/problem+json` and map `errors[].field` onto form controls

Rules: `.claude/rules/angular-ui.md` · Skill: `job-posting-ui`
Spec: @../docs/specs/job-posting.spec.md

# UI · Angular candidate capture form

Angular 22 (`@angular/cli@22.1.8`), standalone components, signals, SCSS. Implements the candidate module (`docs/specs/candidate.spec.md`): a single capture form at `/candidates/new` and `/candidates/:id/edit`. There is no job posting UI yet in this repo.

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| Node.js | 22.12+ (Angular 22 actually needs 22.22.3+/24.15+/26+ — see Troubleshooting) | `node -v` |
| npm | bundled with Node | `npm -v` |

This machine's default Node was 16.14, too old for Angular 22. A portable Node 22.23.2 was installed at `C:\Users\manik\tools\node-v22.23.2-win-x64`, separate from the system Node, and used only via `PATH` for this project:
```
export PATH="/c/Users/manik/tools/node-v22.23.2-win-x64:$PATH"
```
Put that (or the Windows-native equivalent) in front of `PATH` before running any command below.

## First-time setup

The app was scaffolded with:
```
npx --yes @angular/cli@22.1.8 new ui --directory ui --style scss --routing --ssr false --skip-git --skip-install
```
then the generated files were copied over the existing `ui/CLAUDE.md`. This step does not need repeating — `ui/` already contains the scaffolded project.

Install dependencies:
```
npm install --legacy-peer-deps
```
`--legacy-peer-deps` works around an npm/Arborist crash (`Cannot read properties of null (reading 'edgesOut')`) seen with plain `npm install` on this machine — see Troubleshooting.

## Run, step by step

1. Start the candidate API locally first (see `api/README.md`) — the form has nothing to call otherwise. By default it listens on `http://localhost:8080`.
2. Start the dev server:
   ```
   npm start
   ```
   Expected: `Local: http://localhost:4200/`.
3. Open `http://localhost:4200/candidates/new` in a browser (the app redirects `/` there). Expected: the capture form, with **Basic information** and **Professional information** sections.
4. Fill every required field and submit. Expected: `201 Created` from the API and a success message naming the candidate; the form resets for the next entry.
5. Submit with a field left invalid (e.g. `fullName` of 1 character). Expected: an inline error under that field, and no request is sent.
6. Open `http://localhost:4200/candidates/<id>/edit` for a real id (from step 4's `Location` header or the API response). Expected: the form loads pre-filled, and saving sends `PUT` with the loaded `version`.
7. Open the edit route for an id that does not exist. Expected: a not-found message, not an empty form.

**Not yet verified in a real browser** — this environment has no browser to click through with. Steps 3-7 above were validated by: the API integration checks in `api/README.md`, unit/component tests (below) covering the same scenarios, and confirming `curl` gets `200`/CORS headers from both `localhost:4200` and `localhost:8080`. Please click through once before relying on this for a demo.

## Test and build commands

| Command | What it runs |
|---|---|
| `npm test -- --watch=false` | Component and service tests (Vitest, the Angular 22 default runner). 23/23 passing as of this write-up. |
| `npm run build` | Production build to `dist/ui/browser/`. |

There is no `npm run lint` script — Angular 22's `ng new` no longer scaffolds ESLint by default, and none was added here (adding `angular-eslint` is a new dependency; ask before adding it, per the project's dependency rule).

## Config

`src/environments/environment.ts` and `environment.development.ts` set `apiBaseUrl` to `http://localhost:8080` (the API's `LocalServer` default port). Terraform overwrites `environment.ts` with the deployed Function URL at deploy time (`infrastructure.spec.md`) — never hard-code the API URL elsewhere.

## Structure

```
src/app/core/i18n.service.ts            reads src/i18n/en.json — the only source of user-facing text
src/app/shared/chip-input/              reusable chip input (Enter/comma adds a chip, rejects case-insensitive duplicates)
src/app/features/candidate/
  candidate.routes.ts                   lazy routes: new, :id/edit
  data/candidate.model.ts               types matching the API JSON exactly
  data/candidate.service.ts             the only place that calls /api/v1/candidates
  form/                                 the capture form (new + edit modes), plus unsaved-changes.guard.ts
src/environments/                       apiBaseUrl lives here only
src/i18n/en.json                        all user-facing labels
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ng new` fails with "The Angular CLI requires a minimum Node.js version of v22.22.3" | Node 22.20.0 is not new enough — Angular 22.1.8 needs an exact 22.22.3+/24.15+/26+ patch. Install the latest Node 22.x LTS, not just "any 22". |
| `npm install` fails with `Cannot read properties of null (reading 'edgesOut')` | A known npm/Arborist peer-resolution bug (seen with npm 10.9.8 resolving `vitest`'s optional peers). Run `npm cache clean --force` then `npm install --legacy-peer-deps`. |
| Browser console shows a CORS error calling `localhost:8080` | The local API's `LocalServer` only allows the origin in `ALLOWED_ORIGIN` (default `http://localhost:4200`). Confirm you're running `ng serve` on port 4200, or set `ALLOWED_ORIGIN` to match. |
| Form submits but nothing happens | Confirm the API is running (`api/README.md`) and `environment.ts`'s `apiBaseUrl` matches its port. |
| `409` shown as a generic error instead of the reload prompt | Only a `type` ending in `version-conflict` triggers the prompt; confirm the API is returning the problem+json shape from `docs/specs/candidate.spec.md`. |

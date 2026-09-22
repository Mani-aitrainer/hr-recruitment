---
paths:
  - "ui/src/**/*.ts"
  - "ui/src/**/*.html"
  - "ui/src/**/*.scss"
---
# Angular UI rules

- Angular 22 standalone components only, with no NgModules. Use `ChangeDetectionStrategy.OnPush`.
- Use `inject()` for dependencies, signals for component state, and `input()`/`output()`/`model()` instead of decorators.
- Use built-in control flow (`@if`, `@for` with `track`, `@switch`). Don't use `*ngIf`/`*ngFor`.
- Only `JobPostingService` calls the API, through `HttpClient`. No `fetch()`, and no HTTP calls in components.
- The API base URL comes from `environment.ts` only.
- Reactive, typed forms (`FormGroup<...>`). Validators must match `docs/specs/job-posting.spec.md` field rules exactly.
- Keep business logic out of templates. Move anything beyond a simple expression into a `computed()` signal.
- All user-facing text goes in the i18n file. No hard-coded labels.
- Accessibility: every control has a `<label>`, errors are linked with `aria-describedby`, and every action works from the keyboard.
- Each component and service has spec tests covering its acceptance criteria: at least one **positive** (valid input renders or submits) and the **negative** ones that apply (invalid form blocks submission, boundary values, server `400` mapped to controls, `409` conflict, `404`, and the empty and error states). Mock HTTP with `provideHttpClientTesting`.
- No new npm dependency without saying why.

# Wrong-password inline message on /login

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Auth tracer-bullet: login → list of Accounts

## What to build

When the login mutation returns HTTP 401, `useLogin().onError` consumes the error before the global handler observes it: it calls a caller-provided `setError('root', { message: 'Incorrect email or password.' })` on the LoginPage's React Hook Form instance. `<LoginPage>` renders any `errors.root?.message` as a prominent inline region above the form fields, with `role="alert"` (or equivalent ARIA semantics) so a screen reader announces the message immediately. The global mutation `onError` toast does _not_ fire for this case — the per-call `onError` consumes the error first. Every other error from the login mutation continues to surface as the generic toast per ADR-0005.

This is the single hard-coded `MalaiCode → human copy` mapping for v1. No translation table is introduced anywhere; if a future case needs another mapping, that is the signal to introduce the table (ADR-0005).

## Acceptance criteria

- [ ] `useLogin().onError`: when the response status is 401, calls the caller-provided `setError` with `('root', { message: 'Incorrect email or password.' })` and does not invalidate the user query.
- [ ] `useLogin().onError`: for any non-401 error, does not call `setError` (the global toast handler takes over).
- [ ] `<LoginPage>` renders `errors.root?.message` in a prominent region above the form, with `role="alert"` (or equivalent), only when the message is present.
- [ ] Vitest unit spec covers both branches of `useLogin().onError`: the 401 path (setError called with the canonical copy; user query not invalidated) and the non-401 path (setError not called).
- [ ] Playwright sad-path spec submits an invalid password against the shared Cognito user, asserts the inline message "Incorrect email or password." appears above the form, and asserts no Sonner toast appears.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, `npm run build`, and `npm run test:e2e` all pass.

## Blocked by

- [02-login-happy-path.md](./02-login-happy-path.md)

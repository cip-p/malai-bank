# Login happy path: submit valid creds → land on Accounts page

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Auth tracer-bullet: login → list of Accounts

## What to build

Replace the placeholder `/login` page with a real form: an email field, a password field, and a submit button, all rendered via shadcn form primitives. The form uses `useForm({ resolver: zodResolver(zLoginRequest), shouldFocusError: true })` — `zLoginRequest` is the schema generated under `src/api/generated/` from the OpenAPI contract, per ADR-0004. Submission drives `useLogin().login(...)`. On success the user query is invalidated; the `<Authenticated>` gate observes the now-authenticated state and renders the placeholder `<AccountsPage>` at `/`.

`<AppShell>` chrome wraps every protected page. For this slice the shell renders the signed-in User's display name (sourced from the `/api/v1/user` response); the Sign out button arrives in issue 05, the disabled theme switcher placeholder is already in place from issue 01. The placeholder `<AccountsPage>` renders `<h1>Your accounts</h1>` and a "Signed in as {displayName}" line — real Account rows arrive in issue 04.

Per ADR-0004 no hand-authored Zod schema duplicates the generated one. Per ADR-0001 the only signal "I am signed in" is the user query — there is no Zustand auth mirror.

## Acceptance criteria

- [ ] `<LoginPage>` renders email + password fields with visible labels, plus a submit button.
- [ ] The form uses `zodResolver(zLoginRequest)` directly — no hand-authored copy of the login schema exists in the codebase.
- [ ] Client-side validation fires before submit: empty fields and malformed email show inline messages adjacent to the offending field.
- [ ] `useLogin()` is implemented; `onSuccess` invalidates the user query; happy path causes the `<Authenticated>` gate to flip to authenticated and render the placeholder Accounts page at `/`.
- [ ] `<AppShell>` chrome wraps the placeholder `<AccountsPage>` and renders the signed-in User's display name from `/api/v1/user`.
- [ ] `<AccountsPage>` placeholder renders `<h1>Your accounts</h1>` and a "Signed in as {displayName}" line.
- [ ] Vitest unit spec covers `useLogin` happy path: a successful mutation invalidates the user query and does not call `setError`.
- [ ] Playwright happy-path spec (without preloaded `storageState`) drives the form with the shared Cognito user, submits, and asserts landing on `/` with the placeholder AccountsPage and the User's display name visible.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, `npm run build`, and `npm run test:e2e` all pass.

## Blocked by

- [01-app-boots-with-auth-gate-redirect.md](./01-app-boots-with-auth-gate-redirect.md)

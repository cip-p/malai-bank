# Signout round-trip

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Auth tracer-bullet: login → list of Accounts

## What to build

`<AppShell>` renders a Sign out button. Clicking it calls `useSignout()`, which wraps `POST /api/v1/auth/signout`. On success the QueryClient clears at least the user query, the AccountHolders query, and the Accounts query. The `<Authenticated>` gate observes the now-unauthenticated user query and redirects to `/login` — no explicit `navigate('/login')` is needed in the signout handler; the gate is the only place navigation-on-auth-state-change lives.

This slice closes the tracer-bullet's full round-trip: a user can sign in, see their Accounts, sign out, and re-confirm the session is gone (revisiting `/` redirects again). Per CONTEXT.md and ADR-0001 the redirect is implicit in the user query going from authenticated to unauthenticated.

## Acceptance criteria

- [ ] `useSignout()` is implemented; `onSuccess` clears at least the user query, the AccountHolders query, and the Accounts query from the QueryClient.
- [ ] `<AppShell>` renders a "Sign out" button that triggers `useSignout()`.
- [ ] After a successful signout the `<Authenticated>` gate redirects to `/login` without an explicit `navigate` call in the signout handler.
- [ ] Playwright signout spec (using preloaded `storageState`) visits `/`, clicks Sign out, asserts redirect to `/login`, then revisits `/` in the same browser context and asserts a second redirect to `/login` — proving the session was actually cleared rather than only the UI state.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, `npm run build`, and `npm run test:e2e` all pass.

## Blocked by

- [04-active-holder-and-accounts-list.md](./04-active-holder-and-accounts-list.md)

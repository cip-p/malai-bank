# Active AccountHolder resolution + real Accounts render on the Accounts page

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Auth tracer-bullet: login → list of Accounts

## What to build

The placeholder `<AccountsPage>` becomes real. After authentication the page fetches the User's reachable AccountHolders via `GET /api/v1/account-holder/all`, derives the **Active AccountHolder** as the first entry with `accountHolderType === 'INDIVIDUAL'`, and then fetches `GET /api/v1/accounts` with the `MalAccountHolderIdHeader` set to that holder's UID. The list renders one row per Account (the Account display name plus identifiers if cheaply available from the response). A skeleton state shows while either query is loading.

`useActiveAccountHolder()` is the deep hook. Per CONTEXT.md a `null` result _after_ the holders query resolves throws `InvariantViolation` — every signed-in User has exactly one Individual holder by Registration. The hook also reads a `useActiveHolderOverrideStore` Zustand slice (always `null` in v1) ahead of the API derivation, so the future switcher can override without rippling through call sites.

`useAccounts()` is a thin wrapper that pulls `activeHolderId` from `useActiveAccountHolder()` and injects the holder header automatically. Pages never construct the header themselves; the rule lives in one place.

Per CONTEXT.md no other holder types are surfaced in v1. Per ADR-0001 both queries 401-redirect via the global handler if the session expires mid-load.

## Acceptance criteria

- [ ] `useActiveAccountHolder()` reads `/api/v1/account-holder/all` and returns the first entry with `accountHolderType === 'INDIVIDUAL'`.
- [ ] `useActiveAccountHolder()` throws `InvariantViolation` when the resolved response contains no Individual holder.
- [ ] A `useActiveHolderOverrideStore` Zustand slice exists with a `null` default and is consulted ahead of the API derivation; setting the override changes which holder the hook returns.
- [ ] `useAccounts()` injects the holder header automatically from `useActiveAccountHolder()`; no call site constructs the header by hand.
- [ ] `<AccountsPage>` renders one row per Account (display name + identifiers when present); a skeleton renders while either underlying query is loading.
- [ ] Vitest unit specs cover `useActiveAccountHolder`: picks Individual among multiple holder types in any order; throws when no Individual is present; surfaces query loading and error states without throwing.
- [ ] Playwright happy-path spec is extended to assert real account rows for the shared Cognito user and the active holder's display name appearing as the page subhead.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, `npm run build`, and `npm run test:e2e` all pass.

## Blocked by

- [02-login-happy-path.md](./02-login-happy-path.md)

# Account-not-found page for unknown uid

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

When `useAccount(accountUid)` resolves to `status === 'not-found'` (its `/accounts` list query completed without containing the requested uid), `<AccountDetailPage>` renders `<AccountNotFoundPage>` instead of the hero + **Transactions** composition.

`<AccountNotFoundPage>` is a small page-level component:

- `<h1>Account not found</h1>`, focused on mount via the existing `useFocusOnMount`.
- One line of muted explanatory copy beneath ("We couldn't find that account. It may have been closed or the link may be wrong.").
- A `← Back to Accounts` link to `/`.

No toast fires on the not-found path — it is an expected outcome distinct from a transient query failure.

`/accounts/<an-unknown-uid>` is added to the existing axe a11y spec; zero violations expected.

The not-found branch of `useAccount` was already exercised by unit tests in slice 01; this slice adds the page-level rendering test and the E2E coverage.

## Acceptance criteria

- [ ] When `useAccount` resolves with `status === 'not-found'`, `<AccountDetailPage>` renders `<AccountNotFoundPage>` instead of the hero composition.
- [ ] `<AccountNotFoundPage>` renders `<h1>Account not found</h1>` (focused on mount), a muted explanatory line, and a `← Back to Accounts` link to `/`.
- [ ] No generic toast appears on the not-found path.
- [ ] Unit test asserts `<AccountDetailPage>` routes to `<AccountNotFoundPage>` when `useAccount` returns `'not-found'`.
- [ ] E2E asserts navigating to `/accounts/00000000-0000-0000-0000-000000000000` renders the page with the back link, which on click returns the user to `/`.
- [ ] axe spec passes on `/accounts/<unknown-uuid>` with zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

[01-detail-page-route-and-cache-primed-hero.md](./01-detail-page-route-and-cache-primed-hero.md)

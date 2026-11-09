# Account detail page route + cache-primed hero (name + type only)

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

The home `<AccountsPage>` stops rendering its `<ul>` of bullet rows and becomes a responsive grid of `<AccountCard>` components. Each card is wrapped in a React Router `<Link>` to `/accounts/:accountUid` — the entire card is the click target with a single focus ring per card. In this slice the card body is intentionally minimal: **Account** name and account type only. Balance and identifiers land in slice 02.

A new route `/accounts/:accountUid` under `<Authenticated>` renders `<AccountDetailPage>`, composed of a back link `← Accounts`, an `<h1>` of the **Account** name (focused on mount via the existing `useFocusOnMount`), and the account type beneath. No body content beyond that in this slice — balance / identifiers / **Transactions** sections land in later slices.

The hero's name + type render _instantly_ on navigation from a card via a new deep hook `useAccount(accountUid)`. Internally `useAccount` reads `queryClient.getQueryData(...)` against the `useAccounts` cache key, finds the matching uid, and returns `{ status: 'cache-hit', account }` synchronously. On a deep-link or hard refresh (no cache available), it falls back to a fresh `/accounts` list query and resolves to `{ status: 'ready', account }`. If the fresh list does not contain the uid, it resolves to `{ status: 'not-found' }` — this status is exposed in this slice but rendering the not-found page is left to slice 05.

Per ADR-0001 `useAccount` consumes TanStack Query as the single source of truth for server state. Per ADR-0002 no React Router loader is involved. Per CONTEXT.md the **Account**'s identity and display name are the only domain concepts touched by this slice.

## Acceptance criteria

- [ ] `<AccountsPage>` renders a grid of `<AccountCard>` components in place of the previous `<ul>` of bullet rows.
- [ ] Each `<AccountCard>` is a single `<Link>` to `/accounts/<accountUid>` with the whole card as the click target; tabbing to a card produces exactly one focus ring per card.
- [ ] `App.tsx` declares `<Route path="/accounts/:accountUid" element={<AccountDetailPage />} />` under `<Authenticated>`.
- [ ] `<AccountDetailPage>` renders a `← Accounts` back link, an `<h1>` of the **Account** name (focused on mount via `useFocusOnMount`), and the account type beneath.
- [ ] `useAccount(accountUid)` exposes `{ account, status }` where `status ∈ { 'cache-hit', 'fetching', 'ready', 'not-found' }`.
- [ ] Navigation from a card on `/` to the detail page renders the hero name + type **without a skeleton flicker** (cache-hit path is observable).
- [ ] Deep-linking to `/accounts/<known-uid>` from a fresh context renders a skeleton briefly, then resolves the hero name + type.
- [ ] Unit tests cover `useAccount`'s cache-hit, cache-miss → ready, and cache-miss → not-found paths.
- [ ] E2E test asserts list → click first card → URL changes to `/accounts/<uid>` → hero name is visible immediately (no skeleton flicker).
- [ ] `/accounts/<known-uid>` is added to the existing axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

None — can start immediately.

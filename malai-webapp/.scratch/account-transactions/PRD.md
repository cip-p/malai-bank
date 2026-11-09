# Account detail page + transactions list

Status: ready-for-agent

## Problem Statement

A bank decision-maker watching the malai demo lands on the AccountsPage and sees the list of their **Accounts** — name, type, currency. That's the value the auth tracer-bullet delivered. But on a real bank app, the demo's next instinct is to click an **Account** and see "what's actually in it" — the balance, the sort-code / account-number, and the recent transaction history. Today, clicking an **Account** row does nothing. The cards don't carry balance or identifiers either, so the home page itself reads as a list of _labels_, not a list of _accounts_. To a demo viewer, this is the moment the app stops looking like a real bank.

The Transactions phase closes that gap: re-rendering the home page as a HSBC-style card grid carrying balance + identifiers, and giving each **Account** a dedicated detail page at `/accounts/:accountUid` showing balance, identifiers, and the **Account**'s most recent 100 **Transactions**.

## Solution

Build on the auth tracer-bullet's architecture (`<AppShell>`, `<Authenticated>`, `useUser`, `useActiveAccountHolder`, `useAccounts`) and the patterns it established (TanStack Query is the only source of server state; pages cache-prime from existing queries; ADR-0005 generic-error toast; ADR-0006 a11y baseline). The Transactions phase delivers:

- **Home page upgraded to HSBC-style cards.** Each card shows the **Account**'s name, type, sort-code + account-number, and balance. Each card is a full-width link to the detail page. A master eye-toggle in the page header hides every balance on the list at once.
- **A new `/accounts/:accountUid` detail page.** Hero card (name, balance, identifiers) on top; **Transactions** section below. The hero is cache-primed from the home page so it renders instantly on navigation from a card; deep-link arrivals see a per-section skeleton.
- **Primary navigation in `<AppShell>`.** A horizontal nav with a single visible slot — **Accounts** — in v1. A second slot for **Pay** is shaped in the markup but not rendered until the Payees phase ships (no dead links during a demo).
- **A balance eye-toggle** persisted in `sessionStorage` so the preference survives navigation within a session but resets on signout — appropriate for a shared demo environment used by multiple bank executives.
- **Independent loading and partial-failure rendering** on the detail page. Balance, identifiers, and **Transactions** are three parallel queries, each with its own skeleton; if one fails, the other two still render. The generic-error toast from ADR-0005 surfaces the failure; the page stays useful.

After this slice lands, the architectural seams for the Payees phase (a `Pay` nav slot ready to be revealed) and the Transfers phase (a `/accounts/:uid/...` route shape ready to host `Move money`, a hero with a CTA slot reserved) are already in place.

## User Stories

1. As a **User** on the home page, I want to see each **Account** as a card showing its name, sort-code + account-number, and balance, so that I can take in my financial position at a glance.
2. As a **User** on the home page who values privacy on a shared screen, I want a single eye toggle near the page heading that hides all balances at once, so that I can present the app without exposing money.
3. As a **User** who has hidden balances, I want each card to render `£·····` in place of the amount with the rest of the card unchanged, so that the visual rhythm of the page does not shift.
4. As a **User** who has hidden balances and signs out, I want the next sign-in to start with balances visible again, so that the hidden state does not persist across users on a shared machine.
5. As a **User** on the home page, I want to click anywhere on an **Account** card to open that **Account**'s detail page, so that I do not have to hunt for a tiny chevron.
6. As a **User** navigating with the keyboard only, I want each card to be a single focusable element with a visible focus ring, so that I can step through my **Accounts** without ambiguity.
7. As a **User** landing on `/accounts/:accountUid` from the home page, I want the hero card to appear immediately — name and type already visible, balance and identifiers skeletoning in — so that the navigation feels instant.
8. As a **User** deep-linking to `/accounts/:accountUid` directly (refresh, bookmark, link share), I want the hero card to skeleton its name + balance + identifiers from scratch and then resolve, so that the page works correctly without prior cache.
9. As a **User** on the detail page, I want a back link `← Accounts` clearly at the top, so that I can return to the list without using the browser back button.
10. As a **User** on the detail page, I want the balance prominently displayed with the sort-code + account-number beneath it, so that I can read the two pieces of information real-bank users care about most.
11. As a **User** on the detail page who hid balances on the list, I want my hero balance hidden by default but with a per-page eye toggle I can use to reveal just this one, so that a quick check does not require flipping the master switch.
12. As a **User** who reveals a balance on a detail page, I want the home page list to remain hidden when I return to it, so that the master toggle remains the source of truth for the list.
13. As a **User** on the detail page, I want to copy the sort-code and the account-number to my clipboard with a single click on each, so that I can paste them into another app or message.
14. As a **User** on the detail page, I want a **Transactions** section beneath the hero showing my most recent activity, so that I can see what has moved through the **Account**.
15. As a **User** reading the **Transactions** list, I want entries grouped under "Today", "Yesterday", and explicit dates like "14 May" (or "14 May 2024" when in a prior year), so that I can scan time periods at a glance.
16. As a **User** reading a **Transaction** row, I want the merchant name (or description as fallback) on the left and a signed amount on the right, so that the line reads like a bank statement.
17. As a **User** reading a debit, I want the amount prefixed with `−` (e.g. `−£12.50`), so that I know money left the **Account** without relying on colour alone.
18. As a **User** reading a credit, I want the amount prefixed with `+` (e.g. `+£2,000.00`), so that credits and debits are visually symmetric.
19. As a **User** reading a **Transaction** row, I want a smaller muted running balance underneath the amount (`Bal £1,234.56`), so that I can see the **Account**'s balance at the time of each entry.
20. As a **User** with a category attached to a **Transaction**, I want it rendered as a small muted secondary line under the merchant name, so that I get the extra context without it dominating the row.
21. As a **User** on the detail page of a brand-new **Account** with no **Transactions** yet, I want a single muted line saying "No transactions yet.", so that the empty state is unambiguous and unintimidating.
22. As a **User** on the detail page when the balance query fails but **Transactions** succeed, I want the **Transactions** list to render with a small inline "Couldn't load balance" in the hero, so that the page remains useful when one service is having a moment.
23. As a **User** on the detail page when the **Transactions** query fails but balance + identifiers succeed, I want a small inline "Couldn't load transactions" in the **Transactions** section, so that I still see what I can.
24. As a **User** experiencing any backend failure on the detail page, I want a generic non-blocking toast saying "Something went wrong. Please try again." per ADR-0005, so that the failure is acknowledged without me needing to read inline copy.
25. As a **User** deep-linking to `/accounts/<an-unknown-uid>`, I want a page-level "Account not found" with a `← Back to Accounts` link, so that I understand the navigation failed cleanly instead of seeing a blank screen.
26. As a **User** whose session expires mid-use on the detail page, I want the existing global 401 handler to invalidate the user query and redirect me to `/login` per ADR-0001, so that session expiry behaves the same on this page as on every other.
27. As a **User** on a mobile breakpoint, I want the primary navigation to collapse into a hamburger menu with the same items, so that the small screen does not lose access to navigation.
28. As a **User** using a screen reader, I want keyboard focus to land on the detail page's `<h1>` after navigation (per the existing `useFocusOnMount` pattern), so that I hear what page I am now on.
29. As a **User** using a screen reader, I want each section's loading state announced via `aria-busy` (matching the existing AccountsPage skeleton pattern), so that I am not left wondering whether the page is broken or still loading.
30. As a **User** with `prefers-reduced-motion`, I want card hover, focus, and skeleton animations to respect the reduced-motion preference, so that the detail page does not introduce motion the app should have suppressed.
31. As a bank decision-maker watching the demo, I want the detail page rendered only from theme tokens (no literal palette colours), so that the existing rebrand discipline holds for this slice — the ESLint rule from the auth tracer-bullet already enforces this.
32. As a developer reading the codebase, I want a single `formatCurrency(amountMinorUnits, currency)` helper used everywhere money is rendered, so that there is exactly one place to fix the formatting rules if they ever change.
33. As a developer reading the codebase, I want a single `groupTransactionsByDate(transactions, now)` helper that returns the date-grouped structure the list renders, so that the grouping logic is unit-testable in isolation rather than tangled with React.
34. As a developer adding the next per-**Account** sub-page later (Statements, Standing Orders for an **Account**), I want the route shape to already be `/accounts/:accountUid/...`, so that adding `/accounts/:uid/statements` is a one-line route addition.
35. As a developer regenerating the API client after a contract change, I want every page that consumes balance / identifiers / **Transactions** to fail to type-check if the response shape moves, so that drift cannot reach production silently.
36. As a developer working in this repo, I want the master-balance-visibility store to live as a tiny Zustand slice with `sessionStorage` persistence, so that page components do not have to know how persistence works.

## Implementation Decisions

This PRD extends the auth-tracer-bullet architecture (`<AppShell>`, `<Authenticated>`, `useUser`, `useActiveAccountHolder`, `useAccounts`) and the six ADRs already on the branch (`docs/adr/0001` through `0006`). It does not re-litigate any of them; it commits to them as the contract.

**Glossary**: `CONTEXT.md` was updated during the grilling session to add a `Transaction` entry — the UI line item, mapping to one API ledger entry (`TransactionItemResponse`), never deduplicated by the API's underlying `transactionId`. The Relationships section was updated to include `Account → 0..N Transactions, newest-first, API caps at 100`. All copy in this PRD uses the canonical term.

**Data hooks** (thin wrappers over the generated TanStack Query factories; each injects `MalAccountHolderIdHeader` from `useActiveAccountHolder`):

- **`useBalance(accountUid)`** wraps the generated query for `GET /api/v1/accounts/{accountUid}/balance`. Surface: `{ balance, isPending, isError }`.
- **`useIdentifiers(accountUid)`** wraps the generated query for `GET /api/v1/accounts/{accountUid}/identifiers`. Surface: `{ identifiers, isPending, isError }`.
- **`useTransactions(accountUid)`** wraps the generated query for `GET /api/v1/accounts/{accountUid}/transactions`. Surface: `{ transactions, isPending, isError }`. The API caps at 100 newest-first entries with no pagination; the hook does not paginate.
- **`useAccount(accountUid)`** is **deep**. Surface: `{ account, status }` where `status ∈ { 'cache-hit', 'fetching', 'ready', 'not-found' }`. Internally: read `queryClient.getQueryData(...)` against the `useAccounts` cache key, find the matching `accountUid`. If found, return it synchronously (`cache-hit`; hero renders instantly). If absent, fall back to a fresh `/accounts` list query (the deep-link path); if that resolves without containing the uid, status is `'not-found'`. This hook is the single seam responsible for cache-priming the detail page from the list page's cache and for distinguishing the 404 case from an error.

**State**:

- **`useBalanceVisibility()`** is **deep**. Surface: `{ hidden, toggle, setHidden }`. Implementation: a tiny Zustand slice using the `persist` middleware backed by `sessionStorage` (not `localStorage` — see Further Notes). The store is reset to `hidden: false` by `useSignout`'s `onSuccess` alongside the existing query clears, so signout returns the next sign-in to "balances visible". This hook is the _only_ place sessionStorage is touched in the codebase for this preference.

**Pure helpers** (no React, no I/O):

- **`formatCurrency(amountMinorUnits, currency)`** returns a `string` formatted with `Intl.NumberFormat('en-GB', { style: 'currency', currency })`. Handles minor-units → major-units conversion (divide by 100 for GBP). Sign is preserved on the input. Currency-aware for future expansion; only `GBP` is in scope.
- **`formatRelativeDateGroup(date, now)`** returns the date-group label: `"Today"`, `"Yesterday"`, `"14 May"` (current year), or `"14 May 2024"` (prior year). Uses `date-fns` (already in `package.json`).
- **`groupTransactionsByDate(transactions, now)`** returns an ordered `Array<{ label: string; items: TransactionItemResponse[] }>`, preserving the API's newest-first ordering within each group. Each group's `label` is `formatRelativeDateGroup` on the group's date.

**Components**:

- **`<BalanceText amountMinorUnits currency hidden />`** is the shared primitive. Renders `£·····` when `hidden` is `true`, otherwise the formatted amount via `formatCurrency`. Used by `<AccountCard>` and `<AccountHeroCard>`.
- **`<AccountCard account />`** is the home-page card. Internally calls `useBalance(account.accountUid)` and `useIdentifiers(account.accountUid)` (the fan-out). The whole card is wrapped in a React Router `<Link to={`/accounts/${account.accountUid}`}>`. While balance is loading, renders a skeleton block in the balance slot; while identifiers are loading, renders a muted placeholder for the sort-code line. If either query errors, renders an inline `—` in that field (the global toast handles the user-visible notification). The card reads the active balance-visibility state from `useBalanceVisibility`; there is no per-card eye toggle on the home page.
- **`<AccountHeroCard accountUid />`** is the detail-page hero. Reads `useAccount(accountUid)` for cache-primed name + type; fires its own `useBalance` and `useIdentifiers`. Same skeleton / inline-error patterns as `<AccountCard>`. Has a per-card eye toggle that defaults to the master-visibility state but is locally overridable; the local override is component state only and does _not_ propagate to the master store. Sort-code and account-number each have a copy-to-clipboard button next to them. No "Move money" CTA in this PRD — that ships with the Transfers phase.
- **`<TransactionRow transaction />`** renders one row: primary label `merchantName ?? description ?? "Transaction"`; muted secondary line of `category` when present; right-aligned signed amount via `formatCurrency`; right-aligned muted running balance underneath as `Bal {formatCurrency(...)}`. Rows are not interactive (the API exposes nothing additional worth showing in a modal at this point).
- **`<TransactionsList accountUid />`** wraps `useTransactions(accountUid)` and renders: a skeleton (three pulsing row placeholders) while loading; the empty state `"No transactions yet."` when the API returns zero entries; an inline `"Couldn't load transactions"` if the query errors; otherwise the date-grouped list using `groupTransactionsByDate`. Section heading `Transactions` is rendered once above the list regardless of state.
- **`<AccountDetailPage />`** is the route component for `/accounts/:accountUid`. Composes: a back link `← Accounts` at the top, `<AccountHeroCard>`, `<TransactionsList>`. The `<h1>` is the **Account** name (focused on mount via the existing `useFocusOnMount`). If `useAccount` resolves with `status === 'not-found'`, renders `<AccountNotFoundPage>` instead.
- **`<AccountNotFoundPage />`** is a small page-level component: `<h1>` reading "Account not found" (focused on mount), one line of muted explanatory copy, and a `← Back to Accounts` link.

**Modified components**:

- **`<AppShell>`** gains a primary horizontal navigation row beneath the existing app bar. In v1 it contains one item — **Accounts** — wired to `/`. The `Pay` slot is _not_ rendered in v1. The nav is marked up as a list (`<ul>`) so future items append as `<li>`s without reshaping the markup. On `md:` and below the primary nav collapses into a hamburger menu using existing shadcn primitives. Active route is indicated with the same token-based highlight currently used for focus rings.
- **`<AccountsPage>`** is restructured. The `<ul>` of bullet-rows is replaced with a responsive grid of `<AccountCard>`. A page-header row to the right of `<h1>Your accounts</h1>` carries the master balance-visibility eye toggle — an icon button driving `useBalanceVisibility().toggle`, with `aria-pressed` reflecting the hidden state and `aria-label` reading "Show balances" / "Hide balances". The "Signed in as ..." line and active-holder name remain as-is.
- **`App.tsx`** declares the new route under `<Authenticated>`: `<Route path="/accounts/:accountUid" element={<AccountDetailPage />} />`.

**Backend contract dependencies** (no changes required by this PRD):

- `GET /api/v1/accounts/{accountUid}/balance` — used by `useBalance`.
- `GET /api/v1/accounts/{accountUid}/identifiers` — used by `useIdentifiers`.
- `GET /api/v1/accounts/{accountUid}/transactions` — used by `useTransactions`.
- `GET /api/v1/accounts` — already used by `useAccounts`; still the cache source for `useAccount`.

**Cross-repo follow-up (filed separately, not blocking this PRD):**

A backend issue should be filed against `malai-backend` to add `balanceMinorUnits` and a primary identifier (sort-code + account-number) onto each `AccountResponse`, so the home page can render its grid from a single API call rather than the N-fan-out this PRD ships. The fan-out is acceptable at demo scale (3–5 accounts, each query cached, all parallel) but the list contract is genuinely incomplete. The path back is non-breaking: when the fields land in `AccountResponse`, `<AccountCard>` is the only file to change — it reads the fields directly and stops firing the separate `useBalance` / `useIdentifiers` queries. Those hooks remain in use on the detail page where the dedicated payloads are appropriate.

**Cache priming and invalidation**:

- `<AccountDetailPage>` cache-primes the hero via `useAccount` — no explicit hydration calls.
- The Transactions phase has no writes, so no invalidation logic is added in this PRD. The Transfers phase will own the invalidation rule (write a Transfer → invalidate the source **Account**'s `useBalance` and `useTransactions` keys).

**Error rendering recap (no change from ADR-0005)**:

- Every uncaught query / mutation error surfaces as the generic toast.
- The detail page's inline "Couldn't load X" copy is _in addition to_ the toast, not instead of it — purely a layout cue so the section does not look empty.
- 404 from `useAccount` (cache miss + fresh list lookup that does not contain the uid) is **not** an error; it is the `'not-found'` status that triggers `<AccountNotFoundPage>`. No toast fires.

## Testing Decisions

What makes a good test in this codebase (inherited from the auth-tracer-bullet PRD):

- Tests assert externally observable behaviour, not implementation details. `useBalanceVisibility` is tested via what `hidden` reads after `toggle` / `setHidden` calls — not by inspecting Zustand internals.
- Tests reuse the OpenAPI-defined types and the generated Zod schemas to construct fixtures. A contract change makes invalid fixtures fail to compile rather than silently pass the wrong shape into a test.
- E2E tests drive the real local webapp against the real local backend per `malai-webapp/CLAUDE.md` (no MSW or mocks in E2E; no Playwright `webServer` auto-spawning). They reuse the shared Cognito user and `storageState` from `tests/e2e/auth.setup.ts`.
- No comments in any test file. Intent comes from `describe` / `it` names, fixtures, and assertions.

**Unit tests (Vitest, `npm test`)**:

- **`formatCurrency`**: positive amount in GBP → `"£12.50"`; negative amount → `"−£12.50"` (Unicode minus, the en-GB locale default); zero → `"£0.00"`; large amount with thousands separator (`200000` minor units → `"£2,000.00"`). Currency parameter respected even though only GBP is in scope.
- **`formatRelativeDateGroup`**: a date that is `now` → `"Today"`; the calendar day before `now` → `"Yesterday"`; an earlier date in the current year → `"14 May"`; a date in a prior year → `"14 May 2024"`. `now` is passed explicitly so tests are deterministic.
- **`groupTransactionsByDate`**: empty input → `[]`; a single transaction → one group with one item; transactions spanning three days (today / yesterday / earlier in current year) → three groups in the correct order with the correct labels; within-group ordering preserves the API's newest-first order.
- **`useBalanceVisibility`**: initial `hidden` is `false`; `toggle()` flips it; `setHidden(true)` sets it; a fresh store instance reading `sessionStorage` observes the previously-persisted value; clearing `sessionStorage` resets the next read to `false`. (No need to test the Zustand library itself — only the public surface this hook exposes.)
- **`useAccount`**: cache-hit path — given a `useAccounts`-prepopulated query cache containing the requested uid, the hook returns `{ status: 'cache-hit', account }` on first render with no network call; cache-miss path — given an empty cache, the hook fires `/accounts` and resolves to `{ status: 'ready', account }`; not-found path — given an empty cache and a fresh `/accounts` response that does not contain the uid, the hook resolves to `{ status: 'not-found' }`.

**E2E tests (Playwright, `npm run test:e2e`)**:

- **List → detail navigation, cache-primed hero** — with the shared signed-in storageState, visit `/`, assert at least one `<AccountCard>` renders. Click the first card. Assert the URL changes to `/accounts/<uid>`. Assert the hero card's name + type are visible _immediately_ (no skeleton flicker — the cache prime is observable). Assert balance + identifier values appear within the page's normal query resolution window.
- **Direct deep-link to detail** — open a fresh context with the storageState but no prior in-app navigation; navigate directly to `/accounts/<known-uid>`. Assert hero skeletons resolve to populated values, **Transactions** section resolves to its list, no "not found" copy appears.
- **404 on unknown uid** — navigate to `/accounts/00000000-0000-0000-0000-000000000000`. Assert "Account not found" appears with a `← Back to Accounts` link that returns to `/`.
- **Balance visibility round-trip** — visit `/`, click the master eye toggle, assert all card balances render `£·····`. Click into the first card. Assert the hero balance is hidden by default. Click the local hero eye toggle, assert the hero balance is revealed. Click `← Accounts`. Assert the list balances are _still_ `£·····` (local reveal did not propagate to the master). Sign out, sign back in, visit `/`. Assert balances are visible again (sessionStorage cleared on signout reset).
- **Transaction row anatomy** — on a detail page for an **Account** with at least one **Transaction**, assert: at least one row shows a primary label, a signed amount matching `/^[+−]£[\d,]+\.\d{2}$/`, and a running balance line beneath. Assert at least one date-group heading is present.
- **Whole-card click target** — tab to a card on `/`, assert the card receives a single focus ring (one focusable element per card), press Enter, assert navigation to the corresponding detail page.

Partial-failure rendering (transactions query 500s while balance + identifiers succeed) is covered by the unit tests on `<TransactionsList>`'s error state and on the page composition rather than by an E2E variant — fault-injecting a single query against the real backend in Playwright is more brittle than the assertion is worth.

**Accessibility (`npm run a11y`)**:

- Add `/accounts/<known-uid>` to the existing axe spec. Must pass with zero violations in both light and dark modes on the `malai` theme.
- The "Account not found" page is covered by visiting an unknown uid in the same spec (single axe scan, zero violations expected).

**Prior art**:

- `tests/e2e/auth.setup.ts` and `playwright/.auth/user.json` already provide the shared sign-in; every E2E test in this PRD consumes that storageState.
- `tests/e2e/a11y.spec.ts` is the existing home for axe scans.
- `src/test/setup.ts` is the existing Vitest setup. The auth tracer-bullet's unit tests for `useUser`, `useLogin`, `useActiveAccountHolder` are the pattern reference for the new unit tests written here.

## Out of Scope

- **Payees** (list, CRUD, `/pay/payees`) — next PRD, Payees phase.
- **Transfers** (wizard, `POST /api/v1/accounts/{accountId}/transfers`, `/pay/transfer`) — PRD after Payees.
- **The `Pay` nav slot itself** — designed but not rendered in v1; revealed when Payees ships.
- **The "Move money" CTA on the hero card** — designed but not rendered in v1; wired in the Transfers phase.
- **Per-Account sub-pages for Statements, Direct Debits, Standing Orders** — Phase 2, after Transfers. The route shape `/accounts/:accountUid/...` is established by this PRD specifically so adding `/accounts/:uid/statements` later is a route-table change, not a re-architecture.
- **A "switch account" affordance on the detail page** — HSBC has it; we do not in v1. Users navigate back to `/` and click another card. Revisit only if a demo shows it as friction.
- **Category icons / colour-coded chips** — `category` is rendered as a muted secondary line of text only. The API returns `category` as a free-form nullable string so no icon mapping is meaningful yet.
- **Counterparty resolution** — `counterpartyId` is a bare UUID with no name-resolution endpoint, so the field is not rendered at all. When the backend exposes a counterparty-name endpoint or inlines the name, the row label fallback chain will incorporate it.
- **Pagination / "View older transactions"** — the API caps at 100 and explicitly forecloses pagination. No "Load more" affordance is rendered.
- **A per-card eye toggle on the home page** — only the master toggle exists on `/`; per-card local reveal lives on the detail page only.
- **Workspace tooling for the `MalaiCode → human copy` table** — still deferred per ADR-0005. The detail page's failures surface as the generic toast plus inline "Couldn't load X" cues.
- **A second named theme** — out of scope; the existing single `malai` theme is reused.
- **An ADR for the home-page fan-out decision** — not written (the borderline ADR case the grilling session flagged). The decision is captured in this PRD and in the backend issue. Revisit if a future change makes the trade-off worth pinning to its own document.
- **An ADR for the eye-toggle persistence decision** — not written. The `sessionStorage` rationale is captured here. Revisit if a future preference (theme, locale, holder switcher) needs the same persistence question answered uniformly.

## Further Notes

- The webapp `CLAUDE.md` elevates `/tdd`. The natural starting move for this PRD is to write the cache-primed list → detail E2E spec first, then drive `<AccountDetailPage>` + `<AccountHeroCard>` + `<TransactionsList>` into existence, then back-fill the pure-helper unit tests as `formatCurrency` / `formatRelativeDateGroup` / `groupTransactionsByDate` emerge during the page work.
- **Why `sessionStorage` for the eye toggle** (not `localStorage`): the demo environment is genuinely shared. Multiple bank executives may sign in to the same browser within minutes. A balance preference that outlived signout would leak across viewers; `sessionStorage` resets cleanly on tab close and is additionally cleared on signout via `useSignout`'s `onSuccess`. The Zustand slice's `persist` config should include a brief inline note pointing back to this reasoning, since the choice is non-obvious without it.
- **Why fan-out N+1 balance / identifier calls on the home page**: the OpenAPI-defined `AccountResponse` does not carry balance or identifiers, and the brief is explicitly "the APIs are present, build pages". At demo scale (3–5 accounts) the cost is one initial load of ~6–10 small parallel cached calls, all reusable across navigation. The accompanying backend issue captures the path back to a single-call list page when the contract is extended; nothing in this PRD's implementation precludes that swap.
- The 404 path uses `<AccountNotFoundPage>` rather than a global 404 route on purpose — the unknown-uid case is the _only_ 404 we can predict at this point and a route-level `<Route path="*" element={<NotFound />} />` would set a precedent we have no other use for yet. Revisit when a second 404 source appears.
- The 401 redirect, generic toast, focus management, skip-to-main-content link, `prefers-reduced-motion` rules, and the ESLint Tailwind-palette ban are inherited from the auth tracer-bullet — no changes in this PRD. The detail page slots in under the existing `<Authenticated>` → `<AppShell>` → `<Outlet />` tree.
- All copy introduced by this PRD ("Your accounts" retained, "Account not found", "No transactions yet.", "Transactions", "Couldn't load balance", "Couldn't load transactions", "Show balances" / "Hide balances", `Bal {amount}`, section / page headings) is the v1 contract. Subsequent PRDs may revisit it but should not silently diverge.

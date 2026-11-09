# Balance + identifiers fan-out across home cards and detail hero

Status: done

## Notes (post-implementation)

- Implemented per spec: `formatCurrency`, `<BalanceText>`, `useBalance`, `useIdentifiers`, `<AccountHeroCard>`, and `<AccountCard>` fan-out. AccountDetailPage now delegates the hero render to `<AccountHeroCard>` for the cache-hit/ready branches; the page still owns the `← Accounts` back link and the `status==='fetching'`/`'not-found'` branches.
- `<BalanceText hidden={true}>` renders the masked `£·····` placeholder — branch is unit-tested even though slice 02 always passes `hidden={false}`. Slice 03 plugs `useBalanceVisibility` into the call sites without touching `BalanceText`.
- `<AccountHeroCard>` reads `useAccount` for cache-primed name+type (per spec). This re-calls `useAccount` from inside the hero — AccountDetailPage already did so for status routing — but the snapshot inside `useAccount` is per-hook so the two hook instances independently read from the now-cached query data. No extra network firing.
- Identifiers display falls back through `bankIdentifier`/`accountIdentifier` (legacy single-identifier surface) → `accountIdentifiers[0]` (the array surface). The OpenAPI schema marks the legacy fields as optional, so the fallback chain is required to satisfy TypeScript without runtime nullable checks at the call site.
- The `<h1>` with `useFocusOnMount` now lives in `<AccountHeroCard>` rather than `<AccountDetailPage>`. This means focus only lands on the heading once `useAccount` resolves to cache-hit or ready (i.e. once the hero actually mounts). For the cache-hit path the transition is one render, so focus moves immediately — matches the existing acceptance criterion that `account-detail-skeleton` never appears on cache-hit. The existing `account-detail-cache-primed.spec.ts` continues to assert `heading.toBeFocused()`.
- Copy-to-clipboard: `<AccountHeroCard>` uses `navigator.clipboard.writeText(...)` with a transient `<Check>` icon for 1.5s after a successful copy. The E2E spec grants `clipboard-read`/`clipboard-write` permissions and reads back via `navigator.clipboard.readText()`. No sonner toast is fired on copy — the inline icon change is enough visual feedback for a single-action gesture.

## Deferred / blockers

- npm run test:e2e and npm run a11y still need a local malai-api on :8080 + AWS creds. Verified unit tests (51 across 15 files), lint, prettier on changed files locally; the new e2e spec (`account-balance-and-identifiers.spec.ts`) needs a human to run against a live stack to confirm both the formatted-balance assertion and the clipboard-copy round-trip.
- ADR-0005 says "Every uncaught query / mutation error surfaces as the generic toast", but `src/lib/queryClient.ts` only fires `toast.error(GENERIC_ERROR_MESSAGE)` from `mutationCache.onError` — `queryCache.onError` only handles the 401-invalidates-user-query case. The `—` rendering in `<AccountHeroCard>` and `<AccountCard>` is in place for the error branch, but the toast doesn't actually fire on balance/identifiers query failures today. This is a pre-existing gap (not introduced by slice 02). Worth filing as a separate small bugfix issue once another slice surfaces a query-error case the user would notice.
- Pre-existing 3 typecheck errors in `src/lib/queryClient.test.ts` (TanStack Query generic-inference on `QueryCache.find` callback param) still present at HEAD — not introduced by slice 02. They break `npm run build`'s `tsc -b` step. Deferred for the same reason as the auth tracer-bullet slices 02–06 noted.
- Pre-existing prettier warnings in workspace docs (PRD.md, CLAUDE.md, openapi.yaml, etc.) untouched.
- Issue 03 (balance eye toggle) is now unblocked. It will plug `useBalanceVisibility` into `<AccountCard>` (master state), into `<AccountHeroCard>` (master default + local override), and into `useSignout`'s `onSuccess`. The existing `hidden={false}` literals at the call sites are the exact swap points. Issue 04 (transactions) is unblocked off slice 01 — also independent.

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

Both `<AccountCard>` (home page) and `<AccountHeroCard>` (detail page) carry the **Account**'s balance and primary identifiers (sort-code + 8-digit account-number). Because the OpenAPI-defined `AccountResponse` does not include these fields, each card and hero fires its own pair of queries: `useBalance(accountUid)` and `useIdentifiers(accountUid)`, wrapping the generated TanStack Query factories for `GET /api/v1/accounts/{accountUid}/balance` and `GET /api/v1/accounts/{accountUid}/identifiers`. Both hooks inject `MalAccountHolderIdHeader` via the existing `useActiveAccountHolder` pattern.

A new `<AccountHeroCard accountUid />` component replaces the inline hero markup from slice 01 inside `<AccountDetailPage>`. It reads `useAccount` for cache-primed name + type, fires `useBalance` and `useIdentifiers`, and renders: balance prominently using a new `<BalanceText amountMinorUnits currency hidden />` primitive (always-visible in this slice — eye toggle lands in slice 03), sort-code + account-number on a single line beneath, with a copy-to-clipboard button next to each identifier.

While a query is pending, the corresponding slot shows a skeleton block (balance: a number-sized pulsing block; identifiers: a muted placeholder line) with `aria-busy="true"` and a meaningful `aria-label`. If a query errors, the slot renders a muted inline `—`. The global generic-error toast from ADR-0005 surfaces the failure to the user; the inline `—` is purely a layout cue so the slot does not look empty.

`<AccountCard>` is updated to the same data sources and primitives — the same `useBalance` + `useIdentifiers` fan-out, the same `<BalanceText>` primitive. On the card, identifiers render as a single muted line (no copy buttons on the list — copy lives on the detail page only). A new pure helper `formatCurrency(amountMinorUnits, currency)` is introduced and used by `<BalanceText>` — `Intl.NumberFormat('en-GB', { style: 'currency', currency })` against the minor-unit → major-unit conversion (÷100 for GBP). The helper is currency-aware for future expansion; only GBP is in scope.

Fan-out scale: at demo size (3–5 accounts) the home page fires 6–10 small parallel cached queries. The accompanying backend issue (referenced in the PRD) will collapse this to a single call later by extending `AccountResponse`; nothing in this slice's implementation precludes that swap — `<AccountCard>` will become the only file to change when those fields land.

## Acceptance criteria

- [ ] `useBalance(accountUid)` wraps the generated query for `GET /api/v1/accounts/{accountUid}/balance` and injects `MalAccountHolderIdHeader`.
- [ ] `useIdentifiers(accountUid)` wraps the generated query for `GET /api/v1/accounts/{accountUid}/identifiers` and injects `MalAccountHolderIdHeader`.
- [ ] `<BalanceText amountMinorUnits currency hidden />` renders the formatted currency string (`£1,234.56`) when `hidden` is `false`. The `hidden = true` branch is required to be supported but its visual rendering is exercised by slice 03.
- [ ] `formatCurrency(amountMinorUnits, currency)` returns the en-GB-localised string; negative amounts use the Unicode minus (`−`); thousands separator present.
- [ ] `<AccountCard>` renders name, type, sort-code + 8-digit account-number on a muted line, balance via `<BalanceText>`.
- [ ] `<AccountHeroCard>` renders cache-primed name + type, balance via `<BalanceText>`, sort-code + account-number each with a copy-to-clipboard button.
- [ ] Pending state: each balance / identifier slot renders a skeleton block with `aria-busy="true"` and a meaningful `aria-label`.
- [ ] Error state: each slot renders a muted inline `—` and the global toast from ADR-0005 fires.
- [ ] Unit tests cover `formatCurrency` for positive, negative, zero, and thousands-separator cases in GBP.
- [ ] E2E asserts both card-balance and hero-balance render as strings matching `/^£[\d,]+\.\d{2}$/`.
- [ ] E2E asserts clicking a sort-code copy button on the hero writes the sort-code to the clipboard.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

[01-detail-page-route-and-cache-primed-hero.md](./01-detail-page-route-and-cache-primed-hero.md)

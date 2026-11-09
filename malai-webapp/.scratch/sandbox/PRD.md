# PRD: Sandbox — a Counterparty catalogue + inbound-payments simulator inside the webapp

Status: ready-for-agent

## Problem Statement

A bank decision-maker watching a malai demo — or an unguided prospect who has registered on `app.fcd8ayxqr72.com` and is exploring without a guide — has no way, from inside the product, to discover _which_ fictional **Counterparties** they can transact with, _what_ will happen when they do, or _how_ to make an incoming **Transaction** appear on one of their **Accounts**. They can use the existing **Move money** surfaces (Send money, Payees) just fine — but they cannot answer questions like "which sort-code + name combination will pass **Confirmation of Payee** with a `MATCH`?", "which destination will produce a returned payment?", or "is there a way to receive money so I can see what an inbound Transaction looks like?"

The platform behind the webapp already has every fixture the answer depends on — `malai-stub`'s `StubFictionalDirectory` carries named **Counterparties** with deterministic outcomes, and the stub has long had endpoints for simulating inbound payments. None of it is reachable from a signed-in user without YAML access and a hand-rolled `curl`.

That gap matters for the malai pitch. The buyer asks "this looks like a real bank, and the architecture beneath it is one we'd actually want to run." A _real_ banking-platform vendor (Mambu, Thought Machine, 10x) exposes exactly this kind of sandbox tier to its bank tenants' QA teams and to fintech builders sitting on the platform. Surfacing it honestly — as a peer-of-Settings sandbox tier inside the webapp — _strengthens_ the platform pitch rather than weakening the bank illusion. (See [ADR-0016](../../docs/adr/0016-sandbox-placement-peer-of-settings.md) for the placement reasoning.)

Today's webapp shows the user no Sandbox surface at all. There is no `/sandbox` route, no Counterparties catalogue, no inbound-payments form, and no entry point from the Profile tab or the desktop user-menu dropdown. The companion `malai-backend` PRD lands the `/api/v1/sandbox/*` endpoints; without a webapp surface, those endpoints have no consumer and the brief's three asks ("see accounts which would allow creating a new **Payee**", "see accounts for various use cases when making a payment", "simulate receiving a payment") cannot be served.

## Solution

A new **Sandbox** surface at `/sandbox`, discoverable from the **Profile tab** on mobile and from the desktop **user-menu dropdown** (in both cases as a peer of **Settings**, not a child). The route renders a two-tab layout — **Counterparties** and **Inbound payments** — that maps one-to-one onto the brief's three asks.

- **`/sandbox`** — redirects to `/sandbox/counterparties` (the catalogue is the discovery surface; the inbound simulator earns its own tab but is the second-priority entry).
- **`/sandbox/counterparties`** — a single table sourced from `GET /api/v1/sandbox/counterparties`. One row per fictional **Counterparty**. Columns: `Bank`, `Name on record`, `Sort code`, `Account number`, `Outcome` (`Settles` / `Returns (REASON)` / `Stays in progress`). One short header sentence explains the **CoP** teaching pattern — _"Type the name shown to get `MATCH`. Type a near-miss to get `CLOSE_MATCH`. Type anything else to get `NO_MATCH`."_ No per-row hints. Each row carries three action buttons: **Save as payee** (deep-links to `/move/payees/new?sortCode=…&accountNumber=…&name=…`), **Send a payment** (deep-links to the Transfer flow's New payee tab with the destination pre-populated), **Receive a payment** (deep-links to `/sandbox/inbound-payments?originatorSortCode=…&originatorAccountNumber=…&originatorName=…`). Prefilled values are editable in their destination flows.
- **`/sandbox/inbound-payments`** — a form: source picker (the originator **Counterparty**, populated from the catalogue and pre-selected when arrived via deep-link), credited Account picker (from the **Active AccountHolder**'s **Accounts**, defaulting to the first), rail radio (**FPS** / **BACS**, default **FPS**), amount input (existing money-input convention), optional reference. Submit calls `POST /api/v1/sandbox/inbound-payments` with a client-generated `Idempotency-Key`. On success: success toast, form reset, and the existing accounts / transactions query cache is invalidated so the credited Account's history shows the new **Transaction** on the next refetch.
- **`AppShell`** — gains a **Sandbox** link in the **Profile tab** (mobile) between Settings and Theme; and in the desktop **user-menu dropdown** between Settings and Sign out. The mobile bottom-tab strip stays at three per [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md).
- **`AddPayeePage`** — reads `?sortCode=…&accountNumber=…&name=…` query params on mount and pre-fills the New Payee form. Fields stay editable so the user can demo `CLOSE_MATCH` / `NO_MATCH` by mutating the name. Defaults preserve the locked Q13 / Q10 contract: the **Save as payee** deep-link prefills `name = nameOnRecord` so the default action narrates the happy path (`MATCH`).
- **`TransferPage`** — reads similar query params and pre-fills the **New payee** tab on the Transfer form (does not change the source-account dropdown's logic). Fields editable.

The catalogue is **static + global** (per [ADR-0017](../../docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md)) — every signed-in **User** sees the same rows; outcomes are a property of the destination, not an armed scheme state. There is no in-product reset; clutter is handled by the existing `demo-reset.sh` flow.

After this PRD lands, the brief's three asks all have a UI home, the unguided prospect can find the catalogue in two clicks from Profile, and the bank exec on a live demo can narrate "watch what happens when I send to this returning account" while staying in the real production-shaped flows.

## User Stories

1. As a signed-in **User** on mobile, I want a **Sandbox** link in the **Profile tab** between Settings and Theme, so that I can discover the Sandbox surface without leaving the established profile-tab IA per [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md).
2. As a signed-in **User** on desktop, I want a **Sandbox** link in the user-menu dropdown between Settings and Sign out, so that I can reach the Sandbox surface with the same number of clicks as Settings.
3. As a signed-in **User** clicking the Sandbox link, I want to land on `/sandbox/counterparties`, so that the catalogue-first IA opens the discovery surface first per [ADR-0016](../../docs/adr/0016-sandbox-placement-peer-of-settings.md).
4. As a signed-in **User** hitting the bare `/sandbox` URL, I want to be redirected to `/sandbox/counterparties`, so that bookmarked or hand-typed root URLs resolve to a meaningful tab.
5. As a signed-in **User** on `/sandbox`, I want a persistent two-tab sub-nav (**Counterparties** / **Inbound payments**) at the top of the section, so that I can move between the catalogue and the inbound simulator without going back to a hub.
6. As a signed-in **User** on `/sandbox/counterparties`, I want to see one row per fictional **Counterparty** with columns `Bank`, `Name on record`, `Sort code`, `Account number`, `Outcome`, so that I can read what each fixture will do before I act.
7. As a signed-in **User** scanning the Counterparties tab, I want a short header sentence — _"Type the name shown to get `MATCH`. Type a near-miss to get `CLOSE_MATCH`. Type anything else to get `NO_MATCH`."_ — at the top of the table, so that I learn the **CoP** behaviour once and apply it to every row.
8. As a signed-in **User** on `/sandbox/counterparties` while `GET /api/v1/sandbox/counterparties` is loading, I want a skeleton or loading state consistent with the rest of the app, so that the empty-table flash does not look like "no data" per the existing convention.
9. As a signed-in **User** on `/sandbox/counterparties` while the API errors, I want a generic error state with a retry affordance, so that transient failures don't leave me staring at an empty page.
10. As a signed-in **User** on a **Counterparty** row whose `outboundOutcome` is `SETTLES`, I want the `Outcome` column to render _"Settles"_, so that I can predict the happy-path outcome at a glance.
11. As a signed-in **User** on a **Counterparty** row whose `outboundOutcome` is `RETURNS`, I want the `Outcome` column to render _"Returns (INSUFFICIENT_FUNDS)"_ (or the matching `ReturnReason`), so that the reason is visible without a click.
12. As a signed-in **User** on a **Counterparty** row whose `outboundOutcome` is `STUCK_PENDING`, I want the `Outcome` column to render _"Stays in progress"_, so that I can demo the in-flight state described by the brief without rail jargon.
13. As a signed-in **User** clicking **Save as payee** on a **Counterparty** row, I want to be deep-linked to `/move/payees/new?sortCode=…&accountNumber=…&name=…` with the form pre-filled, so that the next click submits a payee creation that will yield `MATCH` by default per Q13.
14. As a signed-in **User** arriving at `/move/payees/new` with `sortCode`, `accountNumber`, and `name` query params, I want all three fields pre-populated and editable, so that I can demo `CLOSE_MATCH` by changing one letter or `NO_MATCH` by typing something completely different.
15. As a signed-in **User** clicking **Send a payment** on a **Counterparty** row, I want to be deep-linked to the Transfer flow's **New payee** tab with the destination pre-populated, so that the next click takes me into the existing transfer entry form with the right context.
16. As a signed-in **User** arriving at the Transfer flow with the destination pre-populated from a Counterparty deep-link, I want the source-account dropdown defaulted to the first Account but free to change, so that I retain control of the source.
17. As a signed-in **User** clicking **Receive a payment** on a **Counterparty** row, I want to be deep-linked to `/sandbox/inbound-payments?…` with the originator pre-selected to that **Counterparty**, so that the inbound form picks up exactly where the catalogue left off.
18. As a signed-in **User** on `/sandbox/inbound-payments`, I want a form with: an originator picker sourced from the catalogue, a credited-Account picker scoped to the active **AccountHolder**, a rail radio (**FPS** / **BACS**, default **FPS**), an amount input matching the existing money-input convention, and an optional reference field, so that I can simulate a credible inbound in one screen.
19. As a signed-in **User** on `/sandbox/inbound-payments` whose deep-link query params identify a known **Counterparty**, I want that **Counterparty** pre-selected in the originator picker, so that the path from "Receive a payment on the catalogue row" → "submit" is one click of confirmation.
20. As a signed-in **User** on `/sandbox/inbound-payments` opening the originator picker, I want the catalogue rows listed (name + sort-code + account-number visible), so that I can pick a different originator without typing.
21. As a signed-in **User** on `/sandbox/inbound-payments` with a single **Account**, I want that Account pre-selected in the credited-Account picker, so that the trivial-case demo is single-click.
22. As a signed-in **User** on `/sandbox/inbound-payments` with multiple **Accounts**, I want to pick any of my Accounts as the credit destination, so that I can demo inbounds into Current or Savings.
23. As a signed-in **User** submitting the inbound form, I want the `Idempotency-Key` header generated per submission attempt and the Submit button disabled while the mutation is pending, so that a double-click cannot duplicate the inbound.
24. As a signed-in **User** whose inbound POST succeeded (`202 Accepted`), I want a success toast, the form reset, and the accounts / transactions query cache invalidated, so that navigating back to my Account or refreshing the credited Account's history shows the new **Transaction**.
25. As a signed-in **User** whose inbound POST failed for any reason, I want a generic error toast per [ADR-0007](../../docs/adr/0007-anti-enumeration-error-policy.md), so that the platform never leaks which input was the problem.
26. As a signed-in **User** demoing the platform end-to-end, I want every action on `/sandbox` to route me into the _real_ customer-facing flow (Payees, Transfer, Account history) rather than a sandbox-only mini-flow, so that what the prospect sees is the production-shape surface, not an inventory of test affordances.
27. As an unguided prospect exploring the app on my own, I want the Sandbox surface to be reachable in two clicks from the Profile tab without breaking the bank illusion, so that I can discover and exercise the platform's failure modes without help from a demonstrator.
28. As a bank-exec demonstrator narrating a live walkthrough, I want to be able to direct the prospect to `/sandbox/counterparties`, point at a row, and say "watch what happens when I send to _this_ one", so that the demo dramaturgy is deterministic-by-destination rather than an off-stage console click.
29. As a signed-in **User** with stuck PENDING **Transactions** from the Sandbox accumulating in my Account history, I want them to render with the existing pending-state UI conventions, so that the in-progress state is visually identical to a real-world pending Payment.
30. As a signed-in **User** who wants to clear my Sandbox state, I want to learn that the existing demo-reset flow handles it (no in-product reset button per Q12), so that my expectation is calibrated and I don't search for a missing affordance.

## Implementation Decisions

### Routes and IA

- New top-level route `/sandbox` with a layout component (`SandboxLayout`) rendering the two-tab sub-nav and the active tab's content.
- Nested routes `/sandbox/counterparties` (default; bare `/sandbox` redirects here) and `/sandbox/inbound-payments`.
- `AppShell` updates: add a Sandbox entry to the **Profile tab** (mobile bottom-tab destination) between Settings and Theme; add a Sandbox row to the desktop user-menu dropdown between Settings and Sign out. Per [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md), the mobile bottom-tab strip itself stays at three.

### Modules to build

- `src/sandbox/` — new folder for the Sandbox surface.
  - `SandboxLayout.tsx` — outer route component; renders sub-nav + outlet.
  - `CounterpartiesPage.tsx` — fetches the catalogue, renders the table + per-row action buttons.
  - `InboundPaymentsPage.tsx` — renders the form, handles submit, mutation, success toast, cache invalidation, deep-link query-param consumption.
  - `useCounterparties.ts` — TanStack Query hook around the generated `getApiV1SandboxCounterparties` (or whatever the generated client surface ends up being).
  - `useInboundPaymentMutation.ts` — TanStack Mutation hook around the generated POST.
  - **Deep module: `counterpartyDeepLink(counterparty, target): string`** — pure function returning the path for each of the three CTAs. Single source of truth for the deep-link URL shape; trivially unit-testable.

### Modules to modify

- `src/components/AppShell.tsx` — Sandbox entry in mobile Profile tab and desktop user-menu dropdown.
- `src/pages/move/AddPayeePage.tsx` — read `?sortCode=…&accountNumber=…&name=…` on mount, prefill fields, leave editable.
- `src/pages/move/TransferPage.tsx` — read similar query params, prefill the New payee tab, leave editable.
- `src/App.tsx` — add the `/sandbox/*` routes.
- `openapi.yaml` (the snapshot in this repo root) — refresh after the backend lands the new contract, then `npm run openapi:gen` to regen the typed client.

### Wire contract consumed

- `GET /api/v1/sandbox/counterparties` → `{ counterparties: [{ sortCode, accountNumber, bank, nameOnRecord, outboundOutcome, returnReason? }] }`. `outboundOutcome` is `SETTLES | RETURNS | STUCK_PENDING`. Generated TanStack Query factory used directly.
- `POST /api/v1/sandbox/inbound-payments` → 202. Request body `{ rail: "FPS" | "BACS", creditedAccountId, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, reference? }`. Headers: `MalAccountHolderIdHeader`, `Idempotency-Key` (generated client-side per submit). Generated TanStack Mutation factory used directly.

### Deep-link query-param contract

- **To `/move/payees/new`**: `?sortCode=<SC>&accountNumber=<AN>&name=<NAME>`. Existing `AddPayeePage` modified to read these on mount.
- **To `/move/transfer` (New payee tab)**: same param names, plus whatever the existing Transfer-flow URL scheme uses for source pre-fill (verify during implementation against `TransferPage.tsx`).
- **To `/sandbox/inbound-payments`**: `?originatorSortCode=<SC>&originatorAccountNumber=<AN>&originatorName=<NAME>` — the form reads these and pre-selects the matching **Counterparty** in the originator picker. Mismatched / missing params leave the picker on its default (first row) state.

### Cache + idempotency

- `Idempotency-Key`: generate a UUID v4 client-side per submission attempt of the inbound POST (existing webapp pattern from the Transfer flow per `move-money` PRD).
- On `202` response: invalidate the queries for `/api/v1/accounts` and the credited Account's `/api/v1/accounts/{id}/transactions`, so the new **Transaction** appears on the next refetch.

### Architectural framing

- [ADR-0016](../../docs/adr/0016-sandbox-placement-peer-of-settings.md) — Sandbox placement (already written).
- [ADR-0017](../../docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md) — destination-deterministic outcome (already written).
- `CONTEXT.md` glossary entries added during design grilling: **Counterparty**, **Sandbox**, **Profile tab** widened.
- Existing ADRs that constrain the implementation:
  - [ADR-0001](../../docs/adr/0001-tanstack-query-owns-auth-state.md) — TanStack Query owns auth state.
  - [ADR-0007](../../docs/adr/0007-anti-enumeration-error-policy.md) — anti-enumeration error policy applies to the inbound mutation.
  - [ADR-0009](../../docs/adr/0009-payees-no-edit-no-fake-patch.md) — Payees have no Edit; the deep-link prefill on `/move/payees/new` is _creation_, not edit-of-existing.
  - [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md) — mobile bottom-tab strip stays at three.

## Testing Decisions

Tests cover external behaviour — what the user can do, what API calls fire, what shows on screen — not implementation details (no asserting on hook internals, query keys, or React state).

- **`CounterpartiesPage.test.tsx`** (Vitest + Testing Library). Cover: skeleton renders while loading; rows render once the query resolves with all locked columns visible; the three action buttons per row produce the correct `href` (assert the `counterpartyDeepLink` outputs are wired correctly); the header sentence is present; error state renders on query error. Prior art: `AccountsPage.test.tsx`, `PayeesPage.test.tsx`.
- **`InboundPaymentsPage.test.tsx`** (Vitest + Testing Library). Cover: form fields render with the right defaults; deep-link query params pre-select the matching **Counterparty**; submit fires a POST with the right body and an `Idempotency-Key` header; Submit button disables while pending; success toast and cache-invalidation fire on `202`; generic error toast fires on failure (no specific-error leakage per [ADR-0007](../../docs/adr/0007-anti-enumeration-error-policy.md)); double-click does not fire two POSTs. Prior art: `TransferPage.test.tsx` (idempotency-key + mutation pattern).
- **`AddPayeePage.test.tsx`** (modified). Add a test for the new query-param prefill behaviour: arriving at `/move/payees/new?sortCode=…&accountNumber=…&name=…` populates the three fields; they remain editable; the existing CoP-on-submit flow is unchanged.
- **`TransferPage.test.tsx`** (modified). Add a test for the query-param prefill behaviour on the New payee tab.
- **`AppShell.test.tsx`** (modified). Add a test that the Sandbox link is present in the mobile Profile tab and the desktop user-menu dropdown, and that the bottom-tab strip remains three items.
- **`counterpartyDeepLink.test.ts`** — pure-function unit test for each of the three targets, covering URL-encoding of the `name` param (names may contain spaces / apostrophes). Not strictly requested in the test-coverage answer above, but trivially cheap and prevents URL-shape regressions.
- **`tests/e2e/sandbox.spec.ts`** (Playwright) — end-to-end against a running local backend + stub. Cover: log-in (via the existing shared auth fixture), navigate to `/sandbox` from the Profile tab, see the catalogue, click **Save as payee** on a known row, land on `/move/payees/new` with prefilled fields, submit, see the verdict, return to `/sandbox`, click **Receive a payment** on the same row, see the form pre-populated, submit, see the success toast, navigate to the credited Account, see the new **Transaction** in the history. Prior art: `tests/e2e/move-money.spec.ts` (or the actual file name landed by the move-money PRD).
- **`tests/e2e/a11y.spec.ts`** — extend the existing axe-core suite to cover `/sandbox/counterparties` and `/sandbox/inbound-payments` per [ADR-0006](../../docs/adr/0006-a11y-baseline.md).

The Vitest jsdom suite stays the unit runner; Playwright owns the cross-stack scenario. Both must be green before the PRD is considered done.

## Out of Scope

- **A holder switcher inside Sandbox.** The active **AccountHolder** is resolved upstream by the existing rule (first `INDIVIDUAL` holder). No Sandbox-specific holder switching.
- **Per-user editable Counterparties.** Catalogue is static + global. v2 if a demo demands it.
- **In-product reset.** Lean on the existing `demo-reset.sh` / `demo-reset-local.sh` flow per Q12.
- **A separate "stuck payments" management view.** Stuck `STUCK_PENDING` Transactions live in the user's normal Account history; no Sandbox-specific surface manages them.
- **Manual unstick of individual stuck Transactions.** Out of scope for v1; clear via demo-reset.
- **Per-rail outcome variation per Counterparty.** The Outcome column is a single value; rail-agnostic per Q11.
- **CHAPS / SWIFT / SEPA in the inbound simulator.** v1 ships FPS + BACS only, matching the existing stub endpoints.
- **A Cmd-K palette or floating "Test mode" pill.** The placement decision in [ADR-0016](../../docs/adr/0016-sandbox-placement-peer-of-settings.md) rules out floating affordances for v1.
- **The backend wiring** (`/api/v1/sandbox/*` controllers, `malai-stub-client`, `sandbox-service` module, stub-side directory widening). All backend work is owned by the companion PRD at `malai-backend/.scratch/sandbox/PRD.md` and must land before this slice can be implemented.

## Further Notes

- This PRD pairs with `malai-backend/.scratch/sandbox/PRD.md`. The backend slice should land first so the `openapi.yaml` snapshot in this repo can be refreshed and `npm run openapi:gen` produces the typed Sandbox client.
- All architectural framing decisions are captured in ADRs already written during the design grilling — ADR-0016 (placement), ADR-0017 (outcome model). Backend ADR-0003 captures the corresponding backend-side framing (the dual role of `malai-stub`).
- The `CONTEXT.md` glossary already contains the locked vocab: **Counterparty**, **Sandbox**, **Profile tab** (widened to include Sandbox). Use those terms in user-facing copy and code identifiers; avoid the synonyms flagged in `_Avoid_:` lines.
- The brief's "anything else you might think could be useful" was left open during design grilling. The locked v1 surface is built to be extended additively (per-payment manual unstick, ad-hoc Counterparties, Cmd-K palette) without breaking the contract; do not pre-build those affordances.
- The unguided-prospect audience matters as much as the live-demo audience. When implementing, optimise for the _first-time visitor_ who has never been told what Sandbox is: every row should be self-explanatory from its columns and the header sentence; deep-link buttons should land the user in the real flow with the right context; the inbound form should be obvious without a tooltip tour.

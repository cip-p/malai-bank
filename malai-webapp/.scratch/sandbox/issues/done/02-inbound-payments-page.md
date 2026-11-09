# Sandbox inbound payments page (working form, idempotent submit, cache invalidation)

Status: ready-for-agent

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

Replace the placeholder page on `/sandbox/inbound-payments` (landed in issue 01) with a working form. A signed-in **User** picks an originator **Counterparty**, picks one of their own **Accounts** to credit, picks a rail (**FPS** / **BACS**), enters an amount and optional reference, and submits. The backend (`POST /api/v1/sandbox/inbound-payments` from backend issue 02) creates the inbound; on success the credited Account's transaction history shows the new **Transaction**.

End-to-end behaviour:

- Originator picker is sourced from `useCounterparties` (the same query as the Counterparties tab; TanStack Query dedups). Each option shows name + sort-code + account-number.
- Credited Account picker is sourced from the existing `useAccounts` query, scoped to the active **AccountHolder**. Default selection is the first Account; if there is only one Account, it is pre-selected with no other choice.
- Rail picker: radio buttons for **FPS** (default) and **BACS**. No other rails in v1.
- Amount input uses the existing money-input convention (whatever helper `TransferPage` uses — reuse, do not reinvent). Validation matches the existing rules: positive, `>= £0.01`, `<= £999,999.99`, two decimals max.
- Reference is an optional single-line text field. No character-counter discipline in v1 (matches the simpler inbound-vs-outbound asymmetry).
- New `useInboundPaymentMutation` hook wraps the generated TanStack Mutation factory. Per submit, mint a UUID v4 `Idempotency-Key` (reuse the existing `mintIdempotencyKey` helper from the move-money slice if it exists; otherwise add it as a shared pure helper).
- Submit button disables while the mutation is pending.
- On `202 Accepted` (success):
  - Success toast — copy: _"Inbound payment received."_
  - The form resets (originator stays pre-selected if it arrived via deep-link; otherwise back to default).
  - Cache invalidation: invalidate `useAccounts` and the credited Account's transactions query so a subsequent navigation to `/accounts/<creditedAccountId>` shows the new **Transaction**.
- On failure (any 4xx/5xx): generic error toast per [ADR-0007](../../../docs/adr/0007-anti-enumeration-error-policy.md) — copy: _"Something went wrong. Please try again."_ No field-level error rendering for inbound payments.
- Deep-link query-param consumption: if the URL carries `?originatorSortCode=…&originatorAccountNumber=…&originatorName=…` and a **Counterparty** in the catalogue matches all three exactly, that Counterparty is pre-selected in the originator picker on mount. Mismatched or missing params leave the picker on its default state (the first catalogue row). The deep-link itself is _produced_ by issue 03 — this slice just _consumes_ it so wiring lands in the right place.

This slice does not touch the Counterparties tab or any external page. It strictly replaces the placeholder on `/sandbox/inbound-payments`.

## Acceptance criteria

- [ ] New `InboundPaymentsPage` component replaces the placeholder on `/sandbox/inbound-payments`. Form layout: originator picker, credited Account picker, rail radio (FPS default), amount, optional reference, Submit button.
- [ ] Originator picker uses `useCounterparties`. Each option shows `name + sort-code + account-number` in a readable shape.
- [ ] Credited Account picker uses the existing `useAccounts` hook. Default = first Account. Single-Account case pre-selects with no choice rendered.
- [ ] Rail radio: FPS default; BACS available; no third option.
- [ ] Amount input reuses the existing money-input helper(s) from `TransferPage`. Validation matches the existing rules; rejects negatives, zero, > £999,999.99, and > two decimals.
- [ ] Optional reference field; not required; not validated beyond max length matching the OpenAPI schema.
- [ ] New `useInboundPaymentMutation` hook wraps the generated TanStack Mutation factory. Per submit, mints a UUID v4 `Idempotency-Key`. Sends `MalAccountHolderIdHeader` per the existing convention.
- [ ] Submit button disables while pending.
- [ ] On `202`: success toast (_"Inbound payment received."_), form resets, `useAccounts` query and the credited Account's transactions query are invalidated.
- [ ] On failure: generic error toast (_"Something went wrong. Please try again."_) per [ADR-0007](../../../docs/adr/0007-anti-enumeration-error-policy.md). No leaked specific-error rendering.
- [ ] Deep-link consumption: on mount, if `?originatorSortCode=…&originatorAccountNumber=…&originatorName=…` are present and exactly match a catalogue row, that row is the initial picker selection. Mismatch / missing → default selection.
- [ ] `InboundPaymentsPage.test.tsx` (Vitest + Testing Library) covers:
  - Form renders with the right defaults.
  - Deep-link params pre-select the matching **Counterparty** in the picker.
  - Missing / mismatched deep-link params leave the default selection.
  - Single-Account case pre-selects the credited Account with no choice rendered.
  - Submit fires the POST with the expected body shape and an `Idempotency-Key` header.
  - Submit button disables while pending.
  - Double-click does not fire two POSTs (assert via mock-call-count).
  - Success-toast, form-reset, and cache-invalidation all fire on `202`.
  - Generic error toast fires on failure (and no specific-message leakage).
  - Amount validation prevents submission for the four reject cases above.
- [ ] Playwright spec extended (`tests/e2e/sandbox.spec.ts`): from `/sandbox/inbound-payments`, pick a Counterparty + credited Account + amount, submit, see the success toast, navigate to the credited Account's detail page, see the new **Transaction** in the history.
- [ ] `tests/e2e/a11y.spec.ts` extended to cover `/sandbox/inbound-payments` post-replacement.
- [ ] `npm test`, `npm run test:e2e`, `npm run lint`, `npm run prettier` all pass.

## Blocked by

- [`./01-sandbox-surface-and-catalogue-page.md`](./01-sandbox-surface-and-catalogue-page.md) — needs the `/sandbox` layout, routes, and `useCounterparties` hook.
- [`malai-backend/.scratch/sandbox/issues/02-inbound-post-endpoint.md`](../../../../malai-backend/.scratch/sandbox/issues/02-inbound-post-endpoint.md) — needs the backend `POST /api/v1/sandbox/inbound-payments` endpoint and the regenerated client to carry the mutation factory.

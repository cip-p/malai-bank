# StepFlow + SuccessTick primitives + Transfer and AddPayee multi-step redesigns

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The Move-money path turned into screens-per-decision. After this slice lands, sending money walks through four single-decision screens (source → destination → amount + reference → confirm) and saving a payee walks through three (name → sort code + account number → CoP-check + confirm). Both flows ride on the same reusable `<StepFlow>` primitive — extracted from the existing RegisterLayout pattern — and end on a success-tick animation.

- **`<StepFlow>`** — generalises the RegisterLayout three-step pattern into a reusable primitive:
  - Accepts a list `[{ id, title, render }]`.
  - Exposes `useStepFlow()` to step components for `{ currentIndex, next, back, isFirst, isLast }`.
  - Holds form state across steps via a wrapped React-Hook-Form context — values entered on step 1 survive a back-and-forward.
  - On every step change focuses the new step's `<h1>` per [ADR-0006](../../docs/adr/0006-a11y-baseline.md) #1 extended to in-page transitions.
  - Renders a step indicator ("Step 2 of 4") and a back affordance on every step except the first.
- **`<SuccessTick>`** — SVG-stroke check animation. `motion-safe` gated; renders a static check under `prefers-reduced-motion: reduce`. `aria-hidden` so screen readers do not announce it as decoration; the success message text carries the accessible name.
- **TransferPage** refactored on top of `<StepFlow>`:
  1. **Source**: pick one of the Active AccountHolder's Accounts (pre-selected when `?from={accountUid}` is present).
  2. **Destination**: the existing three-tab destination picker (My accounts / Saved payee / New payee) — preserves all behaviour from the prior Move-money slice including Confirmation of Payee on New payee.
  3. **Amount + reference**: `<Money>`-bearing input with the existing validation rules (£ adornment, decimal input mode, normalisation, bounds).
  4. **Confirm**: review screen summarising every decision. Prominent **Send** CTA; less-prominent **Back**. On send, navigates to a success screen with `<SuccessTick>` and a "Back to {sourceAccountName}" CTA.
- **AddPayeePage** refactored on top of `<StepFlow>`:
  1. **Name**: payee name.
  2. **Sort code + account number**: with the auto-format + paste-strip rules from the prior slice.
  3. **Confirm**: runs Confirmation of Payee, renders the verdict and the three-button CLOSE_MATCH affordance, ends on a success state.

## Acceptance criteria

- [ ] `<StepFlow>` Vitest unit tests cover: rendering step N; `next()` advances; `back()` returns; form state preserved across step changes; focus moves to the new step's `<h1>` on step change.
- [ ] `<SuccessTick>` Vitest unit tests cover: motion-safe renders the animated SVG; `prefers-reduced-motion: reduce` renders the static check; `aria-hidden` set.
- [ ] TransferPage renders four steps: source / destination / amount-reference / confirm. Each step has a single primary decision and a Back affordance from step 2 onward.
- [ ] Step indicator visible on every step ("Step N of 4").
- [ ] `?from={accountUid}` continues to pre-select the source Account on step 1.
- [ ] Existing destination modes (My accounts / Saved payee / New payee), CoP handling, amount validation all preserved verbatim under the new step structure.
- [ ] Confirm step renders a complete summary; on send, success screen shows `<SuccessTick>` + a "Back to {sourceAccountName}" CTA.
- [ ] AddPayeePage renders three steps: name / sort-code + account-number / confirm. CoP runs on submit of the confirm step. Existing verdict handling and "Use the on-record name" / "Keep what I typed" buttons preserved.
- [ ] All bespoke motion respects `prefers-reduced-motion: reduce`.
- [ ] Existing TransferPage and AddPayeePage tests updated to walk the new multi-step flow; new tests added for `<StepFlow>` and `<SuccessTick>` as standalone primitives.
- [ ] axe-core a11y spec extended over every Transfer step and every AddPayee step without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md)
- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md)
- [03-money-balance-accounts-page](./03-money-balance-accounts-page.md) (`<Money>` is used on the amount step and on the confirm summary)

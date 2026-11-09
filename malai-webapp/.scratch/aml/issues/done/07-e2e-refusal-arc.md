# 07 — E2E: the refusal arc

Status: ready-for-agent

## Parent

`.scratch/aml/PRD.md`

## What to build

The conservative-only posture proven in the browser, reusing the reported arc's machinery. Four payments of exactly £3,141.59 to a per-run-unique beneficiary fire structuring (jointly over threshold) while arming the SAR decider's ungrounded draft — the deliberate mirror of the copilot's armed amount.

The arc: fire the pattern → open the new Case → request the SAR draft → the grounding-failure state renders as a recorded fact, never a toast → follow the decision link and pin that the refused raw output is still readable at the record with its verdict absent → back on the Case view, arm and fire close-reported and witness the no-SAR-ruling-in-force refusal rendered as a fact → reject the Alert at its Decision view → close as dismissed succeeds, the noise leaving through the ruled front door → the Case reads CLOSED_DISMISSED.

Same self-healing rules as the reported arc: unique beneficiary per run, terminal close clearing the slate, tolerance of stale alerts from a prior failed run.

## Acceptance criteria

- [ ] The spec runs in the default e2e project and never self-skips
- [ ] The armed pattern raises its Alert and Case, and the SAR request renders the grounding-failure state as a fact
- [ ] The refused draft's raw output is asserted readable at the linked record with no verdict recorded
- [ ] Close-as-reported is refused and the no-SAR-ruling-in-force fact is asserted as rendered
- [ ] After rejecting the Alert at its Decision view, close-as-dismissed succeeds and CLOSED_DISMISSED is asserted
- [ ] The spec reuses the reported arc's helpers and follows the same self-healing rules

## Notes from issue 06

The machinery is in place and reusable:

- `tests/e2e/moveMoney.ts` — `topUpFirstAccount(page, amount, label)`, `saveNewPayee(page, payee)`, `sendToSavedPayee(page, payee, amount, reference)` (returns `{ paymentId, state, accountUid }`).
- `tests/e2e/aml.ts` — `uniqueBeneficiary(label)`, `fireStructuring(page, { beneficiary, amountEach, payments, topUp })`, `investigationRow(ops, accountUid)`, `openInvestigation(ops, accountUid)`, `structuringAlert(ops, beneficiary)`, `appendRuling(ops, "accept" | "reject")`, `armAndFire(ops, testId)`.

Fire the armed pattern with `{ amountEach: "3141.59", payments: 4, topUp: "18000.00" }` — 4 × £3,141.59 = £12,566.36, jointly over AML's £10,000 and each under it, and the drain stays under rapid-in-out's 80% of the top-up.

**Name the spec so it sorts after `aml-reported-arc.spec.ts`** — e.g. `aml-sar-refusal-arc.spec.ts`. Playwright runs files in sorted order, and £3,141.59 is the armed _ungrounded_ SAR amount: if this spec fails mid-arc it leaves an open Case carrying that evidence, and the SAR timeline spans **every** Alert on a Case, so the reported arc's own draft would then be refused. Running reported-first keeps a failure here from cascading. (`aml-refusal-…` would sort _before_ — "ref" < "rep".)

Three facts worth knowing before writing it:

- **Tab visibility gates the polls.** In one browser context only the active tab is visible, and the Case list / Case view set `refetchIntervalInBackground: false` (`usePaymentStatus` inherits the same default), so a poll stops on a hidden tab. `fireStructuring` and `openInvestigation` already call `bringToFront()` on the page they drive; if you add a step that drives one page while asserting on the other, activate the one you're asserting on.

- A beneficiary outside the stub's fictional directory returns `NO_MATCH` from CoP, and pressing through sets `copVerdictOverride: true`, which makes `StubFpsScheme` **return** the payment rather than settle it. Only settled payments reach the AML sweep, so `fireStructuring` saves the payee first and sends through the **Saved payee** tab (no CoP). Don't route the pattern through the New-payee tab.
- Screening never touches these amounts (each is under the £10,000 threshold and the beneficiary isn't watchlisted), which is why neither arc needs `MAL_PAYMENTS_SCREENING_MODE=ENFORCE` and neither self-skips. `fireStructuring` asserts no payment came back `REFERRED`.

## Blocked by

- `06-e2e-reported-arc.md`

# 02 — Playwright: the investigator reads a cited explanation

Status: done
Category: enhancement

## What to build

One Playwright spec closing the loop the backend e2e cannot see: an ADMIN opens a payment-correlated decision on the Ops surface, presses explain, and reads claims with visible source ids and a link to the explanation's own MDR entry.

A second path covers the demo's sharpest moment: the armed ungrounded subject (defined in `malai-backend` issue 05) produces no explanation, and the surface says so plainly while still linking to the record.

Use the existing Ops storage state and the seeded admin persona; do not add fixtures the demo environment will not have.

## Acceptance criteria

- [~] The spec drives the real Ops surface against a running stack: open decision → explain → claims with source ids visible → MDR entry link resolves — **written** (grounded test); the live run is HITL (needs a running ENFORCE stack)
- [~] The refused path renders the grounding-failure state and links to the raw output — **written** (refused test); live run HITL
- [x] No copilot-specific seed data is required — the explanation is produced live — both payments are created live through the customer move-money flow and each explanation is produced live by pressing **Explain**; no copilot seed
- [~] `npm run test:e2e` green — gated on a live stack (HITL); unrun in this AFK pass

## Blocked by

- 01 (`01-decision-page-explain-with-citations.md`) — done
- `malai-backend` issue 05 — **done** (landed the armed subjects; £1,618.03 is the armed ungrounded amount)

## Resolution (2026-07-24)

`tests/e2e/copilot-explanation.spec.ts` — two tests, both off a **live** REFERRED payment to the
watchlist counterparty `Carol Singh`, in the default `e2e` project (the seeded evaluator holds ADMIN
via the registration demo grant, so no new persona). Both `test.skip` in `SHADOW`, exactly as
`payment-screening.spec.ts` does, reported as skipped so a green run never implies an unproven loop.

- **Grounded** — sends £0.50, opens the screening decision from the worklist (`PAYMENT · <id>`
  correlation), presses **Explain this payment**, and asserts cited claims (`explanation-claims`),
  a visible source id (`explanation-claim-source` in `TYPE · id` shape), then follows the record
  link and asserts it resolves to the explanation's **own** distinct MDR entry (href captured, URL
  asserted to land on that id, not-found hidden), still `PAYMENT · <id>` correlated.
- **Refused** — sends exactly **£1,618.03**, the copilot's armed ungrounded amount (`malai-backend`
  copilot issue 05 — the decider fabricates a citation, grounding fails whole), asserts the
  grounding-failure state (`explanation-refused`, "grounding failed"), no claims, then follows the
  record link and pins that the refused **raw output is still readable** on the copilot's own entry
  while its **verdict reads "None recorded."** (issue 05: `COMPLETED`, raw output verbatim, verdict
  absent).

Decisions worth recording:

- **The armed amount forced a balance decision.** Traced the backend: `PaymentsService.initiate`
  places the outbound funds **hold before screening runs**, and the registration seeder's opening
  balance is a random **£500–£15,000** draw — so £1,618.03 is _not_ reliably affordable, and a
  too-poor account is rejected for insufficient funds before it can REFER. The screening spec
  sidesteps this with £0.50; this one can't. So the refused test **tops up first** via a sandbox
  inbound (`/sandbox/inbound-payments`, real demo surface — not copilot seed) and waits for it to
  settle (the credit transaction appears on the account) before paying. The grounded test needs no
  top-up (£0.50 always clears).
- **Record-link resolution is proven against the link's own href**, not just the generic
  `/ops/decisions/:id` shape — the screening decision matches that shape too, so the href is
  captured and the post-click URL is asserted to land on that specific (distinct) explanation
  decision id.
- **No contract or config change.** The snapshot already carries the copilot types (issue 01);
  `openapi:verify` stays green. A non-prefixed `*.spec.ts` auto-joins the `e2e` project, so
  `playwright.config.ts` is untouched — `playwright test --list` confirms both tests bind to
  `[e2e]` and leak into no other project.

Feedback loops: `tsc -b` clean, `npm test` 1190 passed (unchanged — e2e specs aren't run by
Vitest), eslint / prettier / `openapi:verify` clean. Docs: added a CLAUDE.md testing bullet beside
the screening one.

Unrun AFK — the whole point of the spec — the live run needs a running stack with
`MAL_PAYMENTS_SCREENING_MODE=ENFORCE` and dev Cognito. Assumptions only that run can confirm: that a
REFERRED payment grounds on the local (no-agent) stack — per `malai-backend` copilot issue 07 it
cites payment/hold/screening/audit but silently no ledger legs, still `claims.length > 0`, so the
grounded assertions hold; that the sandbox inbound settles before the payment; and that the
topped-up first account is the account the move-money "From" step pays from (the canonical
CURRENT-first demo account).

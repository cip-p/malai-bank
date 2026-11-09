# 02 — Cancel a direct debit, end to end

Status: ready-for-agent

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The customer revokes a mandate from the webapp. Mirror of the standing-order cancel slice with the direct-debit twist: a cancelled mandate stays visible to the customer, greyed out and marked Cancelled, because that is what real UK banking apps do. The direct-debits schema gains status, cancellation timestamp and start-date anchor (backfilled for existing rows consistently with their next collection date). OpenAPI first (DELETE for cancel; new fields on the list response), then controller and service. The webapp confirmation dialog carries the real-bank warning: cancelling the mandate at your bank doesn't cancel your agreement with the company — tell them too. The direct-debit empty state remains deliberately CTA-less: only creditors originate mandates.

## Acceptance criteria

- [ ] OpenAPI edited before controllers; all changes additive, backwards-compatibility check stays green
- [ ] Cancel returns 204; repeat cancel is a 204 no-op; unknown and not-owned IDs both return 404
- [ ] Cancellation flips status and stamps the timestamp — full-entity database assertion; audit event recorded
- [ ] Existing rows backfilled: status ACTIVE, start-date anchor derived consistently with the row's next collection date
- [ ] List response carries status, cancellation timestamp and start date for every row
- [ ] Kensa acceptance tests in the service's existing happy/sad style
- [ ] Webapp: cancel dialog includes the "also tell the company" warning copy; cancelled mandates remain listed, greyed, with a Cancelled badge; scheduled-payments peek excludes cancelled mandates; no create affordance appears anywhere, including the empty state
- [ ] Playwright spec covers the cancel journey and the cancelled-badge rendering; accessibility spec covers the new dialog and badge states in light and dark themes

## Blocked by

None - can start immediately

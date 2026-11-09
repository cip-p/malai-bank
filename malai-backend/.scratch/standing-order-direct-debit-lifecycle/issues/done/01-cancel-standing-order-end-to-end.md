# 01 — Cancel a standing order, end to end

Status: ready-for-agent

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

The first lifecycle mutation for standing orders: a customer cancels one from the webapp and it stops being a live instruction. Cancellation is a state transition, never a deletion — the standing-orders schema gains a status (ACTIVE/CANCELLED), a cancellation timestamp, and the immutable start-date anchor (backfilled for existing rows consistently with their next payment date, populated for the whole table now so later slices don't touch the schema again). The OpenAPI contract is extended first (DELETE for cancel; status, cancellation timestamp and start date on the list response), then the controller and service follow. The webapp list page gains a cancel affordance behind a confirmation dialog; cancelled standing orders disappear from the customer's list and from the scheduled-payments peek, while their history remains untouched in the feed.

## Acceptance criteria

- [ ] OpenAPI edited before controllers; all changes additive, backwards-compatibility check stays green
- [ ] Cancel returns 204; cancelling an already-cancelled order is a 204 no-op; unknown and not-owned IDs both return 404
- [ ] Cancellation flips status and stamps the cancellation timestamp — full-entity database assertion proves no other field changes; an audit event records the action
- [ ] Existing rows backfilled: status ACTIVE, start-date anchor derived consistently with the row's next payment date
- [ ] List response carries status, cancellation timestamp and start date for every row (no server-side filtering; support agents see cancelled rows)
- [ ] Kensa acceptance tests in the service's existing happy/sad style, strict response contracts with no extra properties
- [ ] Webapp: cancel action with confirmation dialog following the existing destructive-dialog pattern; cancelled standing orders hidden from the list; scheduled-payments peek excludes them; query cache invalidated after cancel
- [ ] Playwright spec covers the cancel journey; accessibility spec covers the new dialog in light and dark themes

## Blocked by

None - can start immediately

# 04 — Payee deletion blocked by an active standing order

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

Deleting a payee that an ACTIVE standing order pays is rejected with a conflict and a dedicated error code, so a customer can't strand a live instruction — today that scenario dies as a raw foreign-key violation. Cancelled standing orders never block deletion: the foreign key from standing orders to payees moves to set-null-on-delete, and the cancelled order continues to display from its payee snapshot. The webapp surfaces the block with a clear explanation ("cancel the standing order first"), building on the existing payee-deletion flow which already knows how to find a payee's standing orders.

## Acceptance criteria

- [x] Deleting a payee referenced by at least one ACTIVE standing order returns a conflict with a dedicated error code; the payee remains
- [x] Deleting a payee referenced only by CANCELLED standing orders succeeds; the cancelled order's payee link becomes null and it still renders correctly from its snapshot
- [x] Foreign-key migration to set-null-on-delete; no raw constraint violation reachable through the API
- [x] Kensa acceptance tests cover both the blocked and permitted paths
- [x] Webapp payee-deletion flow explains the block and points the customer at the standing order; Playwright and accessibility coverage for the blocked state (specs written; live run is HITL — see note)

## Progress

2026-07-07 — backend slice landed on `main`. `DELETE /api/v1/payees/{payeeId}` now
rejects removal of a payee an ACTIVE standing order pays with 409
`PAYEE_HAS_ACTIVE_STANDING_ORDER` (new MalaiCode 6994) before it reaches the database;
a payee referenced only by CANCELLED orders deletes cleanly.

Key decisions:

- The V11 inline FK `standing_orders_payee_id_fkey` defaulted to NO ACTION, so any
  referenced payee died as a raw 500 constraint violation. `V19` flips it to
  ON DELETE SET NULL. ACTIVE-order deletions are blocked in the service layer, so
  SET NULL only ever fires for cancelled links — the cancelled order keeps rendering
  from its `payee_*` snapshot with a null `payee_id`.
- The deletion guard is a native `SELECT EXISTS(... standing_orders WHERE payee_id = ?
AND status = 'ACTIVE')` on `PayeesRepository` — the same modular-monolith shared-SQL
  exception `StandingOrdersRepository.findPayeeSnapshot` uses to read `payees` (no
  cross-service client; ArchUnit blocks only Kotlin-level entity imports). No ownership
  join needed: a standing order's `payee_id` is always the same holder's (create-time
  snapshot verified `findPayeeSnapshot(payeeId, accountHolderId)`), and payee ownership
  is already confirmed before the guard runs.
- The new query is covered end-to-end at the acceptance level (ACTIVE → 409, CANCELLED →
  204 + null link), following this service's convention (no dedicated repository-test
  dir; issue 03 covered its custom repo functions the same way).
- OpenAPI: additive 409 response + description on the existing DELETE payee endpoint
  (oasdiff stays clean). No new module/pom/wiring changes.

Verification: payee-service green (38 tests, incl. new sad block + happy permitted +
22 arch); standing-order-service green (56); malai-commons `MalaiCodeTest` green (6994
unique); V19 applies cleanly (constraint name validated by `malai-db.sh` clean+migrate);
full reactor compiles + installs.

Remaining: the webapp payee-deletion flow (HITL, `malai-webapp` repo) surfaces the block
and points the customer at the standing order, with Playwright + a11y coverage.

2026-07-08 — webapp slice landed. The delete-payee flow now surfaces the block and
points the customer at their standing orders.

Key decisions:

- Surfaced on two levels. Proactively: `DeletePayeeDialog` splits into two variants keyed
  on `linkedStandingOrders.filter(status === "ACTIVE")`. When a payee has an ACTIVE
  standing order the dialog can't confirm — it drops the destructive Remove, explains
  "You can't remove X while a standing order still pays them. Cancel the standing order
  first, then remove the payee." (count-aware singular/plural), and offers a
  "View standing orders" link (`Button asChild` → `/move/standing-orders`, testid
  `delete-payee-view-standing-orders`) plus a Close. CANCELLED-only (or no) linked orders
  fall through to the normal confirm, dropping the old — now inaccurate — "they'll continue
  running but lose their saved-payee link" copy. Reactively (safety net for a stale client
  list): `useDeletePayee` maps a 409 whose ErrorResponse `message` is `"6994"`
  (PAYEE_HAS_ACTIVE_STANDING_ORDER) to a specific "cancel the standing order first" toast,
  reverting the optimistic remove; other 409s keep the generic toast.
- The dialog keeps `delete-payee-description` visible in both variants, so the existing
  "open delete dialog" a11y sweep still resolves regardless of which variant renders.
- OpenAPI: synced the additive 409 + description onto DELETE `/api/v1/payees/{payeeId}` in
  the checked-in `./openapi.yaml` snapshot to match the backend source of truth, and
  regenerated the client (`DeleteApiV1PayeesByPayeeIdErrors` gained a 409 — additive only),
  mirroring how 01/02/03 refreshed only their own slice.

Verification: `npm run test` 806 green (14 dialog + 5 hook + 12 page, all updated/added),
`npm run typecheck` + `npm run lint` + prettier clean. The Playwright blocked-state journey
(add payee → active SO → delete blocked → cancel SO → delete permitted) and the light+dark
a11y sweep of the blocked dialog are written to the existing patterns but NOT executed here
— this box has no local malai-api/Postgres/Cognito stack. Next iteration (HITL): run those
two specs live to confirm the 409 round-trip and axe cleanliness, as 01/02 were verified
before merge.

## Blocked by

- 03 — create a standing order (an ACTIVE payee-linked order must be creatable to exercise the block)

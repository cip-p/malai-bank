# Payees: delete (with Standing Order cross-check)

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

Delete affordance on the Payees list, with a confirm modal that cross-checks the loaded **Standing Orders** and upgrades its copy when matches exist. Also activates the SO→Payee link in `StandingOrdersPage` (the slice-02 row already has the link wiring; it goes live now that `usePayees` resolves to populated data).

This issue enforces [ADR-0009](../../../docs/adr/0009-payees-no-edit-no-fake-patch.md): **no Edit affordance.** Delete + re-add is the recovery path for a typo.

- **`useDeletePayee`** mutation over `DELETE /api/v1/payees/{payeeId}`. Optimistic update — row disappears from the cache immediately. `204` confirms; **`404` is treated as success** (idempotent — the desired end-state is reached); any other 4xx falls through to the generic toast per ADR-0007.
- **`findStandingOrdersByPayee`** pure helper: `(sos: StandingOrderResponse[], payeeId: string) => StandingOrderResponse[]`. One-line implementation, but isolated for tests and to be the single seam future SO-cascading logic could grow against.
- **`PayeeRow`** gains a delete affordance (icon button or kebab menu — match the existing destructive-action pattern in the repo). Opens `DeletePayeeDialog`.
- **`DeletePayeeDialog`** — modal built on the existing dialog primitive. Copy decided by the SO cross-check, computed at open-time against the already-loaded `useStandingOrders()` data (no extra fetch):
  - **0 linked SOs** → _"Remove **{payeeName}**? You can add them again later."_ with _Cancel_ and _Remove_ (destructive variant) buttons.
  - **≥1 linked SOs** → _"Remove **{payeeName}**? **{n} standing order(s)** reference this payee — they'll continue running but lose their saved-payee link."_ with the same buttons.
- On confirm: fire `useDeletePayee`. Optimistic remove + success toast _"Payee removed."_ on 2xx. 4xx (other than 404) → revert optimistic update, show generic toast, leave the row in place.
- **SO→Payee link in `StandingOrdersPage`** — no code change required if slice 02 wired the link conditional on `usePayees().data?.find(p => p.payeeId === so.payeeId)`. Add a Playwright E2E assertion that the link now resolves and clicking it scrolls the Payees list to the matching row (using the fragment anchor pattern).

## Acceptance criteria

- [ ] `useDeletePayee` mutation tested for: happy path (204 + invalidation), 404-as-success (no error toast, row stays removed), 4xx fallthrough (optimistic revert + generic toast).
- [ ] `findStandingOrdersByPayee` unit-tested for: empty SOs, no match, single match, multiple matches.
- [ ] `PayeeRow` delete affordance is keyboard-accessible (focusable, activatable via Enter / Space); follows the existing destructive-action visual pattern.
- [ ] `DeletePayeeDialog` copy matches the PRD verbatim for both the 0-linked and ≥1-linked cases; the count `{n}` is correct; the linked-SOs detection uses the already-loaded `useStandingOrders()` data (no extra network call).
- [ ] Focus returns to a sensible element after dialog dismiss (the _Add payee_ button or the next row's action, per the existing focus-return pattern).
- [ ] `StandingOrdersPage` renders `payeeName` as a link to `/move/payees#${payeeId}` when the SO's `payeeId` resolves to a Payee in `usePayees()`; otherwise plain text.
- [ ] Playwright E2E spec covers: delete a payee that has 0 linked SOs (basic confirm copy); delete a payee that has ≥1 linked SOs (upgraded copy with count); click an SO row's payeeName link and verify the Payees list is scrolled to the matching row.
- [ ] `tests/e2e/a11y.spec.ts` extended to scan the open delete dialog.
- [ ] No Edit affordance is added anywhere. Per [ADR-0009](../../../docs/adr/0009-payees-no-edit-no-fake-patch.md).
- [ ] No comments anywhere — including tests.

## Blocked by

- 03-payees-list-add-with-copa

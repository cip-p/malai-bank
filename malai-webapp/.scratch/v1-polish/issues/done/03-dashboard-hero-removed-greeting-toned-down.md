# Issue 03: Dashboard total-balance hero removed, greeting toned down, dead code cleaned up

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

Three coupled changes on the AccountsPage, all landing in one slice because they touch the same screen and share dead-code cleanup:

1. **Remove the `TotalBalanceHero` card.** UK retail-banking apps (Monzo, Starling, Chase UK, Lloyds, Halifax, Barclays) don't aggregate balances across accounts on their home view. The hero reads as PFM-app, not bank-app. Rationale captured in [ADR-0014](../../docs/adr/0014-per-account-balances-no-aggregate-hero.md).
2. **Relocate the master "Show / Hide balances" toggle** as a small icon button next to the "Accounts" section heading. The privacy-in-public affordance is preserved, just smaller and quieter. The underlying `useBalanceVisibility` store and per-card / per-hero behaviour remain unchanged — every place that already honours the master state continues to.
3. **Tone down the greeting.** Drop the time-of-day modifier. The greeting becomes "Welcome back, {firstName}" using the existing `firstNameOf` helper for the fallback to email. The `greetingFor()` helper becomes dead and is deleted.

Once the hero is gone, the **dead code cleanup** lands in the same slice:

- `TotalBalanceHero` (the sub-component inside AccountsPage)
- `useTotalBalance.ts` (the n+1 balance fan-out — no longer needed)
- `Balance.tsx` and `Balance.test.tsx` (only call site after Issue 02 was AccountsPage; with the hero gone, the component is fully orphaned)
- `greetingFor` helper if unused after this change

The post-change AccountsPage structure: greeting → quick actions → "Accounts" section heading (with master visibility toggle icon-button at its right) → accounts list.

No regression for the Active AccountHolder name display: today it sits inside the hero. With the hero gone, the holder name moves under the greeting (small muted text) so the "which holder am I acting as" cue is preserved.

## Acceptance criteria

- [ ] `TotalBalanceHero` is removed from the AccountsPage; no aggregate balance figure renders anywhere on `/`.
- [ ] The greeting heading reads "Welcome back, {firstName}" with no time-of-day modifier; the `greetingFor()` helper is deleted.
- [ ] The Active AccountHolder name still renders somewhere visible on the dashboard (e.g. under the greeting), preserving the holder cue.
- [ ] A small "Show balances" / "Hide balances" icon button sits at the right of the "Accounts" section heading; clicking it toggles `useBalanceVisibility.hidden` exactly as before; every Account card and the AccountDetailPage hero continue to honour the master state.
- [ ] `useTotalBalance.ts` (and its test file) is deleted; no callers remain.
- [ ] `Balance.tsx` and `Balance.test.tsx` are deleted; no callers remain (this is the second call site going away; Issue 02 already migrated the first).
- [ ] No `n+1` per-Account balance fetch fires on the AccountsPage initial paint just to compute an aggregate (the per-card balance fetches each card already does are unchanged).
- [ ] `AccountsPage.test.tsx` is updated: drop the total-balance hero assertions; assert the master visibility toggle now lives at the section heading and still toggles per-card balances.
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes.

## Blocked by

- Issue 02 (Remove Balance count-up on AccountHeroCard) — must land first so the `Balance.tsx` deletion in this issue is unambiguous.

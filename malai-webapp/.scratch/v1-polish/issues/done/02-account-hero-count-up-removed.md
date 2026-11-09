# Issue 02: Remove Balance count-up on AccountHeroCard

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

The AccountDetailPage's hero card today renders the balance via a `Balance` component that animates the value from `£0.00` to its true figure over ~36 frames on first paint. The client reads this as gamification. Remove it.

Swap the `Balance` usage inside the AccountHeroCard for the existing static `Money` primitive. The balance renders at its true value on first paint with no animation in either motion mode.

The masked-state rendering (`£·····` placeholder when the local-hidden toggle is on) and the per-card visibility-toggle button behaviour are unchanged. The hero's `aria-label` containing the formatted amount, the loading-skeleton state, and the error state (`—`) are all unchanged.

`Balance.tsx` itself is **not** deleted in this issue. The AccountsPage total-balance hero is still using it; that deletion lands in issue 03 once both call sites are off.

No other surface changes. The dashboard hero card behaviour, the account-list cards, the transaction rows, and the transfer review screen are not touched here.

## Acceptance criteria

- [ ] `AccountHeroCard` imports `Money` instead of `Balance` and renders the unmasked balance via `<Money minorUnits=... />`.
- [ ] No animation is visible on first paint of the AccountDetailPage hero — the balance appears at its true value immediately.
- [ ] `prefers-reduced-motion: reduce` behaviour is unchanged (still static — no regression).
- [ ] The masked-state `£·····`, the loading skeleton, the error `—`, and the per-card visibility-toggle behaviour are all unchanged.
- [ ] `AccountHeroCard.test.tsx` is updated to assert against `Money` rendering instead of `Balance`.
- [ ] `Balance.tsx` and `Balance.test.tsx` are NOT deleted in this issue (the dashboard hero still uses them).
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes.

## Blocked by

None - can start immediately.

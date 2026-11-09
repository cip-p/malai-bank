# Issue 07: Hide "My accounts" mode in Transfer when the Active AccountHolder has one Account

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

On the Transfer flow's Destination step, when the **Active AccountHolder**'s `accounts` list has length 1, suppress the **My accounts** option from the segmented control entirely. Currently the option renders and clicking it shows an empty-state message ("You only have one account — nothing to send to yourself"). The empty-state is honest but it invites the user into a dead end; dropping the option is cleaner.

When the _My accounts_ option is suppressed, the segmented control renders only **Saved payee** and **New payee**. The initial sub-mode is **Saved payee** when at least one Payee exists, otherwise **New payee**.

When the holder has two or more Accounts, the three-option segmented control renders unchanged.

The hide-behaviour is a function of the loaded `accounts` list. While the list is still loading, the segmented control's initial render should not flash the wrong shape — either delay rendering the segmented control until `accounts` is loaded, or render a skeleton placeholder, or default to the three-mode shape and let the suppression happen post-load with no visible flicker (whichever produces the smoothest paint).

No other Transfer-flow change in this issue. Source step is unaffected (one-account holders have only one source — that's a separate, pre-existing behaviour the Source step already handles fine).

## Acceptance criteria

- [ ] When the Active AccountHolder has exactly one Account, the Transfer Destination step's segmented control renders only "Saved payee" and "New payee" — no "My accounts" option.
- [ ] In that case the initial sub-mode is "Saved payee" if at least one Payee exists, otherwise "New payee".
- [ ] When the Active AccountHolder has two or more Accounts, the segmented control renders all three options as today.
- [ ] No visible flicker during loading: the segmented control either renders only after `accounts` resolves or starts in a state that does not flash the wrong shape.
- [ ] The empty-state copy "You only have one account — nothing to send to yourself" is removed (it was guarding the now-unreachable case).
- [ ] `TransferPage.test.tsx` covers both branches: one-Account holder (no "My accounts" option, initial mode is Saved payee or New payee), multi-Account holder (three options as today).
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes.

## Blocked by

None - can start immediately.

Coexists with issue 05 (auto-advance) and issue 06 (verb-led CTAs); whichever lands second rebases trivially.

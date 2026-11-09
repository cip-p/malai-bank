# Issue 06: Verb-led CTAs in the Transfer flow

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

Replace the generic "Continue" labels in the Transfer flow with verb-led copy that tells the user what the click does next. The labels:

- **Source step** → "Choose source" (disabled-state label until a source is selected; if Issue 05 has landed, the button is hidden post-selection)
- **Destination step**, _My accounts_ sub-mode → "Choose destination" (or hidden post-selection if Issue 05 has landed)
- **Destination step**, _Saved payee_ sub-mode → "Choose destination" (or hidden post-selection if Issue 05 has landed)
- **Destination step**, _New payee_ sub-mode → "Verify payee" (the click triggers the Confirmation of Payee check, not just a step transition — the label should match what actually happens)
- **Amount step** → "Review"
- **Review and send step** → "Send money" (preferred over "Send {amount}" to keep snapshot stability and avoid amount-string coupling in tests)

The CoPa close-match panel's existing CTAs ("Use the name on record", "Edit", "Send anyway") are unchanged — those already match what they do.

The Success screen's "Back to {sourceAccountName}" CTA is unchanged.

The disabled vs enabled state behaviour for each button is unchanged: a button is disabled when its step's "would have allowed Continue" condition is false.

## Acceptance criteria

- [ ] Transfer Source step CTA reads "Choose source" when visible.
- [ ] Transfer Destination _My accounts_ sub-mode CTA reads "Choose destination" when visible.
- [ ] Transfer Destination _Saved payee_ sub-mode CTA reads "Choose destination" when visible.
- [ ] Transfer Destination _New payee_ sub-mode CTA reads "Verify payee".
- [ ] Transfer Amount step CTA reads "Review".
- [ ] Transfer Review step CTA reads "Send money".
- [ ] The CoPa close-match panel CTAs and the Success screen CTAs are unchanged.
- [ ] `TransferPage.test.tsx` is updated to assert the new label strings; any other test that asserts the old "Continue" label is updated.
- [ ] `tests/e2e/keyboard.spec.ts` and any other Playwright spec referencing the old labels is updated.
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes.

## Blocked by

None - can start immediately.

Coexists with issue 05 (auto-advance) and issue 07 (single-Account holder UX); whichever lands second rebases trivially.

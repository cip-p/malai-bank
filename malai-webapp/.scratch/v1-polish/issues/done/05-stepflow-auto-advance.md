# Issue 05: StepFlow auto-advance on confirmed radio selection

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

Extend `StepFlow` with an opt-in `autoAdvanceOnSelection?: boolean` per-step flag (default `false`). When a step opts in:

- The user clicks a radio (or presses **Space** / **Enter** on a focused radio) → `next()` fires immediately.
- The user navigates between radios via **arrow keys** (the native roving-tabindex / focus dance) → `next()` does **not** fire. The user can compare options before committing.

The distinction matters: arrow-key navigation is how keyboard-only users _browse_ options; click / Space / Enter is how they _commit_. Auto-advance must only fire on commit.

Implementation detail (taken from [ADR-0015](../../docs/adr/0015-step-flow-auto-advance-on-confirmed-selection.md)): React's `onChange` for a radio fires both for click and for arrow-key navigation, so the radio handler can't distinguish on its own. Use a small `useAutoAdvance(active: boolean)` hook (or equivalent wrapper) that attaches `pointerdown` and `keydown:Space|Enter` sentinels, flipping a transient "this change was confirmed" flag the `onChange` handler reads. Arrow-key change events see the flag false and do not advance.

Apply the opt-in to the Transfer flow:

- **Source step** → `autoAdvanceOnSelection: true`.
- **Destination step** — opt in conditionally: `true` when the active sub-mode is _My accounts_ or _Saved payee_ (both are pure radio bodies); `false` when the active sub-mode is _New payee_ (multi-field form + CoPa check on Continue).
- **Amount step**, **Review and send step** → no change (keep `false`).

When auto-advance is wired on a step, the underlying Continue button is hidden once a selection has been made (the auto-advance has taken care of forward progress). Pre-selection, the disabled-state Continue (with whatever label) remains visible as the "what's missing" cue.

The back-step affordance and the `StepFlow` form-state preservation are unchanged: tapping back after auto-advance lands on the prior step with the selection preserved.

## Acceptance criteria

- [ ] `StepFlow`'s step config accepts `autoAdvanceOnSelection?: boolean` (defaults to `false`); existing call sites that don't pass it are unchanged in behaviour.
- [ ] A new `useAutoAdvance` hook (or equivalent wrapper) encodes the "confirmed selection only" rule — click, Space, or Enter advance; arrow-key navigation does not.
- [ ] Transfer **Source** step auto-advances on confirmed radio selection.
- [ ] Transfer **Destination** step auto-advances when in _My accounts_ or _Saved payee_ sub-mode; _New payee_ keeps its explicit Continue button.
- [ ] Transfer **Amount** and **Review** steps are unchanged.
- [ ] The Continue button on opt-in steps is hidden after a selection is made (auto-advance fires) and visible pre-selection (in its disabled state).
- [ ] Vitest unit coverage on the `useAutoAdvance` hook + `StepFlow` integration: clicking a radio advances; Space and Enter on a focused radio advance; arrow keys (`ArrowDown` / `ArrowUp`) navigate between radios _without_ advancing.
- [ ] `TransferPage.test.tsx` is updated to cover the auto-advance behaviour on Source and on the two radio-only Destination sub-modes; the New-payee case still requires Continue.
- [ ] `tests/e2e/keyboard.spec.ts` is extended with a Transfer walkthrough case proving arrow-through-then-Enter behaviour (arrow does not advance; Enter does).
- [ ] No a11y regression: radios remain inside a `role="radiogroup"` with `aria-checked` honoured.
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes; `npm run a11y` passes.

## Blocked by

None - can start immediately.

If issue 06 (Verb-led CTAs) or issue 07 (Single-Account holder UX) lands first, the Source / Destination step buttons in this issue's diff rebase trivially.

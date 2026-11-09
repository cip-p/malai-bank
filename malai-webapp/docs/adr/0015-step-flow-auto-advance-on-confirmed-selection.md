# Step-flow auto-advance on confirmed radio selection

In `StepFlow`-driven wizards (today: the Send-money transfer flow at `/move/send`), a step that consists of **a single radio-group with no other inputs** auto-advances to the next step the moment the user **confirms** a selection. Steps with text inputs, multi-field forms, or mixed controls keep their explicit Continue button.

This is a deliberate departure from the "every step ends in Continue" pattern most form wizards default to. The motivation, from demo feedback: _"when the user has presented options, … just by selecting one option would go to the next page."_ A click to pick + a click to confirm reads as bureaucratic when the pick was already the commitment.

## The rule, precisely

A step auto-advances when:

1. Its rendered body is a single radio-group (one decision, no text inputs, no extra controls).
2. The selection is **confirmed**, not merely focused. "Confirmed" means a mouse/touch click, a `Space` keypress, or an `Enter` keypress on the radio. **Arrow-key navigation between radios does _not_ fire auto-advance** — keyboard-only users routinely arrow through options to compare before committing, and auto-advancing on arrow would steal that pause.
3. The step would have allowed `next()` anyway (i.e. the selection is valid; the underlying Continue button would have been enabled).

A step does **not** auto-advance when it includes any of: text inputs, multi-field forms, segmented controls that change which body renders below them, or an asynchronous check that runs on Continue (e.g. Confirmation of Payee on a new payee).

## Applied to the transfer flow

| Step                                 | Body                                                                     | Auto-advance?                  |
| ------------------------------------ | ------------------------------------------------------------------------ | ------------------------------ |
| Source                               | Single radio over the Active AccountHolder's Accounts                    | ✔                              |
| Destination — _My accounts_ sub-mode | Radio over other Accounts                                                | ✔                              |
| Destination — _Saved payee_ sub-mode | Radio over Payees                                                        | ✔                              |
| Destination — _New payee_ sub-mode   | Name + sort code + account number + Save checkbox, then CoPa on Continue | ✘ (multi-field + async CoPa)   |
| Destination — mode segmented control | Changes which body renders below                                         | ✘ (control, not commitment)    |
| Amount                               | Amount input + reference input                                           | ✘ (text inputs)                |
| Review and send                      | Read-only review + Send CTA                                              | ✘ (final commitment, explicit) |

## Why this is a real trade-off

Auto-advance has a known cost: **users sometimes pick wrong, want to see the next step's shape, then go back to adjust**. Continue gives them a pause to reconsider; auto-advance whisks them forward immediately. Two mitigations make this acceptable for the radio-only case:

- The wizard supports back navigation (browser back, or a back affordance in `StepFlow`), so "I changed my mind" costs one tap to reverse.
- The "confirmed selection" rule, _not_ "focus changed", keeps keyboard users in control — they can arrow-compare without triggering the jump.

## Considered options

- **A — Auto-advance only on single-radio steps with confirmed selection (accepted).** Captures the demo-feedback intent without breaking multi-input steps.
- **B — Auto-advance every step that has any selection.** Includes the mode segmented control on Destination, which would jump before the user sees the body it controls. Rejected — surprises the user.
- **C — Keep explicit Continue everywhere.** Doesn't satisfy the feedback.
- **D — Auto-advance on focus (arrow-through).** Hostile to keyboard-only users; conflicts with [ADR-0006](./0006-a11y-baseline.md). Rejected.

## When this ADR is superseded

If we add a step-flow elsewhere that _should_ auto-advance despite having mixed controls (e.g. a settings wizard where one toggle commits and the rest are optional), revisit the "single radio-group only" constraint. Until then, the rule above is the contract.

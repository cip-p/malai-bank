# Staff-surface refusals read domain codes; customer surfaces stay generic

ADR-0005 committed to no error-code translation in v1 and named "the second case where the demo audience needs better error copy" as the signal to revisit. The AML **Close actions** are that second case: `close-dismissed` and `close-reported` refuse with domain-coded 409s (`AML_CASE_HAS_ACCEPTED_ALERT` 6990, `AML_SAR_NOT_ACTIONABLE` 6989, `AML_CASE_ALREADY_CLOSED` 6988), and the PRD makes the refusal itself the demo — "a case with confirmed suspicion cannot be waved away" is a beat the evaluator must see, which a generic toast would delete.

We consume the trigger by narrowing scope rather than introducing the global table. The rule is now: **staff surfaces (Ops) may map domain codes to designed refusal states, rendered inline as facts, never toasts** — the Gate panel's "the refusal is the feature" posture, extended from status-sniffing to code-reading where one status carries several meanings. **Customer surfaces remain bound by ADR-0005 as written**: generic copy, one login exception, and ADR-0020's stronger silence for screening states. The split follows the disclosure boundary the app already has: the Ops surface is where the platform explains itself; the Banking surface stays in character.

## Considered options

- **Ops-scoped code map (accepted).** Three codes, copy mirroring the contract's own language, unknown codes falling back generic. No drift surface worth tooling for.
- **Status-only, per the Gate panel.** Works when a 409 has one meaning per endpoint; the close endpoints have two each, so the stale-view path would render wrong copy.
- **The global translation table.** ADR-0005's named successor; rejected because the customer surfaces still must not translate (ADR-0007, ADR-0020), so a global table would exist mostly to serve surfaces forbidden from using it.
- **Pre-disabling actions the client predicts will refuse.** Rejected: the Gate decides, the UI displays — pre-disabling prevents the refusal the demo exists to show.

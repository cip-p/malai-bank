# Payees: Create + Delete only, no fake-PATCH edit

The Payees surface in v1 exposes only the mutations the API exposes: `POST /api/v1/payees` to create, `DELETE /api/v1/payees/{payeeId}` to remove. **There is no Edit affordance.** A user who mistyped a payee's name, sort code, account number, or nickname must delete the payee and re-add it. When `PUT /api/v1/payees/{payeeId}` (or a `PATCH` equivalent) lands in the API, this ADR is superseded and an Edit affordance is added.

This is the deliberately uncomfortable option. The comfortable one — exposing an "Edit" button in the UI that under the hood does `DELETE` + `POST` — was rejected for two reasons that compound:

1. **It fakes a contract the OpenAPI doesn't have.** The workspace [CLAUDE.md](../../../CLAUDE.md) names OpenAPI as the source of truth: _"when adding a domain, edit `openapi.yaml` first, then implement controllers to match."_ A "smooth" Edit affordance backed by delete-then-recreate is the webapp leading the contract. Bank-platform evaluators care about that discipline; absent the discipline, every other contract claim becomes weaker.
2. **It silently changes the `payeeId`, which `StandingOrderResponse.payeeId` references.** The spec marks that field `nullable: true`, which means the backend has a defined behaviour for the orphan case — but the spec doesn't tell us which (cascade-null vs `409`-reject), and either way the UI has now made the user's previously-saved Standing Order changes-of-state on their behalf, invisibly, in the name of saving them one typo correction. The cost-to-correctness ratio is bad.

## Delete behaviour (unchanged by this ADR, captured for completeness)

- Confirm modal before `DELETE`. Copy default: _"Remove **`{payeeName}`**? You can add them again later."_
- **SO cross-check** before opening the modal: the Payees hub already loads `/api/v1/standing-orders`, so a client-side `.filter(so => so.payeeId === thisPayeeId)` is free. If any SO references this payee, the modal upgrades to: _"Remove **`{payeeName}`**? **`{n}` standing order(s)** reference this payee — they'll continue running but lose their saved-payee link."_
- The UI does not touch the SO record itself; whatever the backend does with the orphaned `payeeId` (cascade-null is the most likely behaviour given the schema) is what happens.
- A `404` from the DELETE is treated as success (idempotent — already gone). A `409` would fall through to the generic toast per [ADR-0007](./0007-anti-enumeration-error-policy.md) / [ADR-0008](./0008-insufficient-funds-inline-error-carve-out.md).

## Considered options

- **A — Create + Delete only, no Edit (accepted).** Matches the API verbatim. Brutal for typo recovery but honest. When `PUT /payees/{id}` lands, lift the restriction.
- **B — Edit affordance implemented as `DELETE` + `POST`, transparent to the user.** Comfortable UX. Two problems compounding: fakes a contract, and silently issues a new `payeeId` that any linked Standing Order will lose. Rejected.
- **C — Editable nickname only, immutable name / sort code / account number.** Same implementation as B (no partial-update endpoint exists), with most of the same risk and a fraction of the apparent UX benefit. Rejected.

## When this ADR is superseded

When `PUT /api/v1/payees/{payeeId}` (or `PATCH`) is added to the OpenAPI snapshot at `./openapi.yaml`:

1. Regenerate the API client (`npm run openapi:gen`).
2. Add an Edit affordance to the Payee row.
3. Re-run [Confirmation of Payee](./0008-insufficient-funds-inline-error-carve-out.md) when the edit changes name, sort code, or account number — same three-verdict handling as the create path.
4. Mark this ADR `superseded by ADR-NNNN`.

The Edit affordance lands at that point and not before.

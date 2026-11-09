# 02 — Ledger: per-account floor guard

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

Give the ledger's funds guard a per-account **floor**: a **Ledger account** may register with a floor (in minor units, typically negative), and any **Posting** or **Hold** that would take the account's **Available balance** below its floor is rejected atomically inside the operation — account locked, available checked, applied-or-rejected in one transaction, exactly like today's guard. Existing policies are unchanged in behaviour: `NO_OVERDRAFT` ≡ floor 0, `ALLOW_NEGATIVE` ≡ unbounded. The floor is the ledger-side enforcement of the **Credit limit** — a credit card account registers with floor = −creditLimit (wired in issue 08); the guard remains the ledger's invariant, not the caller's.

Semantics are recorded in `ledger-service`'s CONTEXT.md (**Ledger account**, funds-guard invariant).

## Acceptance criteria

- [ ] Ledger account registration accepts the floored guard shape (per-account floor in minor units); existing `NO_OVERDRAFT` / `ALLOW_NEGATIVE` registrations are untouched and regression-green
- [ ] A posting that would take a floored account's available balance below its floor is rejected whole, atomically, with no entries written — asserted via full-entity database assertions
- [ ] A hold that would take available balance below the floor is rejected with no hold row created
- [ ] A posting/hold landing exactly on the floor is accepted (boundary inclusive)
- [ ] Credits to a floored account are always accepted (the floor restricts the negative direction only; a positive balance on a floored account is legal)
- [ ] Guard check uses **Available balance** (ledger balance − held amount), so an active hold consumes floor headroom
- [ ] Any new custom repository functions are tested per repo rule
- [ ] `malai-ledger-client` registration method carries the floor; WireMock-backed client test covers request construction and response deserialisation
- [ ] Kensa acceptance tests in the existing ledger happy/sad style, strict Kacey response assertions with `noExtraProps()`

## Blocked by

None - can start immediately

## Completion note (2026-07-11)

Done. The funds guard now enforces a per-account floor.

**Design**
- `LedgerGuardPolicy` gains a third value `FLOORED`; the account carries a nullable
  `floorMinorUnits` (V21 migration, `floor_minor_units BIGINT`). The guard policy resolves to
  a single **effective floor** via a computed `LedgerAccountEntity.guardFloorMinorUnits`:
  `NO_OVERDRAFT` → 0, `ALLOW_NEGATIVE` → null (unbounded), `FLOORED` → the registered floor.
  This is the one home for the mapping — Hibernate ignores the getter-only property (FIELD access),
  confirmed by schema-validation passing on `floor_minor_units` only.
- `PostingEngine` and `HoldManager` replaced their `guardPolicy == NO_OVERDRAFT` /
  `available < 0` checks with `floor = account.guardFloorMinorUnits; if (floor != null &&
  available < floor)`. For `NO_OVERDRAFT` this is byte-identical to the old zero check, so
  existing NO_OVERDRAFT/ALLOW_NEGATIVE behaviour is regression-green (proven: ledger 37/0/0,
  account-service 47/0/0). A credit is always accepted because it raises available; the floor
  only bites the negative direction.
- Reused the funds-guard rejection (`LEDGER_INSUFFICIENT_FUNDS`, 422) for a floor breach —
  card-service (issue 08) maps that single ledger rejection to `INSUFFICIENT_FUNDS` vs `OVER_LIMIT`
  by account type; the ledger stays a dumb primitive with one funds code.
- Registry normalises the floor: `FLOORED` **requires** `floorMinorUnits` (else
  `BadRequestException(LEDGER_ACCOUNT_FLOOR_INVALID)`, new code 7967 → 400), and the floor is
  forced to null for the other policies, so the stored floor and the register response never
  misrepresent the guard. Registration stays idempotent on accountId.
- No new custom repository functions (reused `ledgerBalanceOf` / `heldAmountOf`), so the
  repo-test rule is satisfied vacuously. No OpenAPI change: the ledger accounts endpoint is
  internal-only, absent from the hand-authored public contract.

**Client**: `malai-ledger-client` `RegisterLedgerAccountRequest`/`Response` gain an optional
`floorMinorUnits` (NON_NULL serialisation → the no-floor path is byte-identical to today, so
account-service register calls are untouched). New WireMock test `registerLedgerAccountWithAFloor`
covers request construction (floor on the wire) and response deserialisation.

**Tests** (Kensa happy/sad, strict Kacey `noExtraProps`): register a `FLOORED` account (response +
full-entity DB assert); posting/hold landing exactly on the floor accepted (boundary inclusive);
credit to a floored account accepted, leaving it positive; debit below the floor rejected (no
entries); hold below the floor rejected (no hold row); a debit rejected when an active hold has
consumed the floor headroom (proves the guard reads Available = ledger − held); FLOORED-without-floor
registration rejected 400/7967 (no account row).

**Verification** (serial mvn, Postgres+WireMock up; re-migrated malai_test for V21 — tests run with
`spring.flyway.enabled=false`): ledger-service 37/0/0, malai-ledger-client 12/0/0, account-service
OnboardingAccountsControllerTest+AccountsControllerTest 47/0/0; whole affected reactor
(ledger-service, ledger-client, account-service, malai-api) compiles+installs clean.

**Notes for next iteration**: issue 08 (credit card application) can now register the credit
account's ledger account with `guardPolicy = FLOORED, floorMinorUnits = -250000`; a breach returns
the funds-guard rejection for card-service to map to the over-limit decline reason.

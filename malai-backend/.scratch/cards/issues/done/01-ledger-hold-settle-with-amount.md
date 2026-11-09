# 01 — Ledger: Hold settle-with-amount (partial capture)

Status: done

## Parent

`.scratch/cards/PRD.md`

## What to build

Extend the ledger's **Hold** settle primitive to accept an optional settle amount ≤ the held amount. In one atomic ledger transaction the hold is marked `SETTLED`, a balanced **Posting** debiting the held account is recorded **for the settle amount**, and the remainder of the reservation is released — so **Available balance** never blips upward mid-operation. Omitting the amount settles the full held amount, preserving today's behaviour for `payments-service`. Settling for more than was held (over-capture) is rejected as a client error. Idempotency on the hold id is preserved: replaying a settle — including a partial one — is a no-op returning the original result.

This is the primitive that lets a pay-at-pump card purchase authorise £120 and present £43.70 (issue 05); it ships independently and is verifiable through the ledger's own surface. Semantics are already recorded in `ledger-service`'s CONTEXT.md (**Hold**).

## Acceptance criteria

- [x] The internal settle operation accepts an optional `amountMinorUnits`; absent → full held amount (existing callers unchanged, `payments-service` acceptance suites stay green untouched)
- [x] Partial settle, in one ledger transaction: hold row `SETTLED`, posting recorded for the settle amount, held amount drops to zero — asserted with full-entity database assertions plus balance-projection assertions (ledger balance reflects the settle amount; available balance = ledger balance immediately after, no residual held amount)
- [x] Over-capture (settle amount > held amount) rejected with a 4xx and no state change (hold still active, no posting)
- [x] Settle amount ≤ 0 rejected with a 4xx
- [x] Idempotent replay of a partial settle with the same hold id returns the original result and posts nothing new
- [x] Funds-guard interaction: a partial settle never triggers a guard rejection (the posting is always within the reservation)
- [x] Any new custom repository functions are tested per repo rule
- [x] `malai-ledger-client` gains the optional-amount settle method; WireMock-backed client test covers request construction (method, URL, service token, body with and without the amount) and response deserialisation
- [x] Kensa acceptance tests in the existing ledger happy/sad style with strict Kacey `containsProp(json { … noExtraProps() })` response assertions

## Blocked by

None - can start immediately

## Completion note (2026-07-11)

Shipped as an additive, wire-backward-compatible extension of the existing full-settle
primitive — no redesign of the caller-supplied-posting model.

Key decisions:
- **The caller still supplies the balanced posting entries; the new `amountMinorUnits` is
  a declared over-capture guard input, not a posting the ledger constructs.** A card
  presentment (issue 05) knows its own scheme-settlement counterparty and merchant name,
  exactly like `payments-service` does today, so the ledger stays a dumb post-and-release
  primitive. `amountMinorUnits` is validated only against the *held* amount.
- **New flat `SettleHoldRequest` (server + client), a superset of `PostTransactionRequest`
  with an optional `amountMinorUnits` between `traceId` and `entries`.** Because the client
  request mapper serialises `NON_NULL`, the no-amount path is *byte-identical* to today's
  `PostTransactionRequest` body → `payments-service` acceptance suites stay green untouched
  (verified: 77/77) and the settle endpoint still deserialises legacy bodies (`amountMinorUnits`
  → null). Avoided polluting the shared `PostTransactionRequest`.
- **Validation guarded by `hold.status == PLACED`**: over-capture (`amount > held`) and
  non-positive (`amount <= 0`) both throw `BadRequestException(LEDGER_SETTLE_AMOUNT_INVALID)`
  (new code 7968) → 400, before the hold flip/posting, so `@Transactional` rollback leaves no
  state change. On replay (`SETTLED`) the check is skipped and `PostingEngine`'s
  transaction-id idempotency returns the original result, posting nothing new.
- **Remainder release is automatic**: flipping the hold to `SETTLED` drops it out of
  `heldAmountOf` (PLACED-only), so within the one transaction the funds guard sees held=0
  and the posting (≤ reservation) can never trip it. Available goes straight from
  `ledger − held` to `ledger − settleAmount`, no upward blip.
- No new custom repository functions (reused `heldAmountOf` / `ledgerBalanceOf`), so the
  repo-test rule is satisfied vacuously. No OpenAPI change: the ledger holds endpoints are
  internal-only, absent from the hand-authored public contract.

Files:
- malai-commons: `MalaiCode.LEDGER_SETTLE_AMOUNT_INVALID(7968)`.
- ledger-service: `SettleHoldRequest` model (+ `toPosting()`), `LedgerHoldsController.settle`
  body type, `HoldManager.settle` amount validation + KDoc; test-support
  `theBalancesForAccount` balance-projection collector; happy tests (partial settle +
  partial-settle replay, both with balance projection) and sad tests (over-capture,
  non-positive).
- malai-ledger-client: `SettleHoldRequest` model, `settleHold` optional
  `settleAmountMinorUnits` param (+ `toSettleHoldRequest` mapper); client WireMock test
  `settleHoldForAPartialAmount` (with-amount body) — the existing `settleHold` test covers
  the without-amount body.

Verification (serial mvn, Postgres+WireMock up; task's npm test/typecheck are N/A in this
Maven/Kotlin repo): ledger-service full 28/0/0 (+ partial-replay → holds-happy 8/0/0),
malai-ledger-client 11/0/0, payments-service full 77/0/0 (existing settle caller unchanged),
malai-api test-compile clean (downstream deploy unit).

Notes for next iteration:
- Issue 05 (presentment) can now settle a card authorisation's hold for the presented amount
  via `ledgerClient.settleHold(token, holdId, posting, presentedAmountMinorUnits)`.
- Over-capture and non-positive share one code/status (400, 7968). If issue 05 wants to
  distinguish "presented > authorised" from a malformed amount at the card layer, it should
  guard in the presentment consumer before calling the ledger (the ledger refuses both the
  same way).

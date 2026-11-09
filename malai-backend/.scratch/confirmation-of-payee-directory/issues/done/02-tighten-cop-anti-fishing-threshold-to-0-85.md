# Tighten Confirmation of Payee anti-fishing threshold to 0.85

Status: ready-for-agent

## Parent

`.scratch/confirmation-of-payee-directory/PRD.md`

## What to build

Raise `MIN_SIMILARITY_FOR_CLOSE_MATCH` in the (relocated) `ConfirmationOfPayeeService` from `0.5` to `0.85`. The verdict shape is unchanged — only the constant moves. Re-tune CLOSE_MATCH acceptance test fixtures so probe-name examples still land in the bucket they intend to under the new threshold.

After this slice the ADR's anti-fishing posture matches the code. An authenticated attacker who knows a victim's `(sort_code, account_number)` can no longer recover the on-record name in 3–5 probes — they have to come within ~1–2 edits of the real name, which is the *point* of typo tolerance, not enumeration. The webapp's three-verdict UX is unchanged because the *bucket* is unchanged; only the threshold for entering CLOSE_MATCH moves.

**Worked examples (post-bump).** On-record `"Octopus Energy Ltd"` (18 chars):

- Probe `"Octopus Enrgy Ltd"` → distance 1 → similarity ≈ 0.94 → `CLOSE_MATCH` ✓
- Probe `"Octavian Energy LL"` → distance ~5 → similarity ≈ 0.72 → `NO_MATCH` (was `CLOSE_MATCH` at the old threshold; the leak vector is closed)

**Binding context.** `malai-backend/docs/adr/0001-confirmation-of-payee-resolves-against-bank-directory-not-saved-payees.md`, anti-fishing section. The ADR documents the residual limitation (a determined attacker with prior knowledge of the victim's name shape can still walk the threshold) and notes the proper future fix — bucket-based `CLOSE_MATCH` without `onRecordName` — as deferred behind a webapp UX change that is explicitly out of scope.

## Acceptance criteria

- [ ] `MIN_SIMILARITY_FOR_CLOSE_MATCH` in `ConfirmationOfPayeeService` is `0.85`.
- [ ] A probe of `(name="Octopus Enrgy Ltd")` against the V14-seeded `"Octopus Energy Ltd"` pair returns `CLOSE_MATCH` with `onRecordName = "Octopus Energy Ltd"`.
- [ ] A probe whose similarity against the on-record name lands in the half-open range `[0.5, 0.85)` returns `NO_MATCH` and the response does not include `onRecordName` — verifies the threshold actually moved and the anti-leak rule still holds.
- [ ] A probe with Levenshtein distance ≤ 2 against the on-record name still returns `MATCH` — verifies the typo-tolerance bucket is untouched.
- [ ] CLOSE_MATCH happy-path and sad-path acceptance fixtures are updated so each test's probe name lands in its intended bucket under the `0.85` threshold (no test relies on similarity in the `[0.5, 0.85)` range to assert `CLOSE_MATCH`).
- [ ] `mvn install` passes from `malai-backend/`.

## Blocked by

`.scratch/confirmation-of-payee-directory/issues/01-relocate-cop-to-account-service-with-stance-y.md`

# Confirmation of Payee federates to `malai-stub` on local-directory miss

Status: ready-for-agent

## Parent

`.scratch/send-and-receive-money/PRD.md`

## What to build

Extend `account-service`'s `ConfirmationOfPayeeService` to fall back to `malai-stub` over HTTP when the local directory doesn't recognise a (sort-code, account-number) pair. The customer-visible behaviour: `POST /api/v1/confirmation-of-payee` for a NatWest/HSBC/Lloyds/Monzo account in the stub's fictional directory returns `MATCH` or `CLOSE_MATCH` (with `onRecordName`) under the same ADR-0001 rules — Stance Y, `MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85`, no `onRecordName` leak on `NO_MATCH`. The verdict shape and response schema are unchanged; the webapp continues to work without regeneration.

The flow is:

```
POST /api/v1/confirmation-of-payee
  → account-service CopVerdictService.verdict(sort, account, name)
     ├─ local directory lookup (account_identifiers ⨝ accounts, filtered to ACTIVE PRIMARY/ADDITIONAL/SAVINGS)
     │     hit  → apply ADR-0001 name-match rules → return verdict
     └─ miss → HTTP POST /cop/lookup on malai-stub
                 stub answers from StubFictionalDirectory using the same name-match rules
                 verdict passes through verbatim
```

This slice adds:

- `malai-clients/malai-stub-client` + `malai-clients/malai-stub-client-test-support` — paired client modules extending `AbstractMalaiClient`. The test-support exposes a `WithMalaiStub` interface with WireMock stub methods for the CoP endpoint and response builders that match the existing `With*` pattern.
- `malai-stub` HTTP surface: `POST /cop/lookup` with request `{ sortCode, accountNumber, name }` and response `{ verdict: MATCH | CLOSE_MATCH | NO_MATCH, onRecordName? }`.
- `StubFictionalDirectory` inside `malai-stub` — seeded with a small set of fictional accounts at fictional UK banks. Names and sort codes look realistic (e.g. NatWest 60-XX-XX, HSBC 40-XX-XX, Lloyds 30-XX-XX, Monzo 04-00-XX, Barclays 20-XX-XX); each fictional bank holds a handful of accounts. The directory is loaded from a YAML or JSON resource at start-up — *not* a database, because the stub's directory is not the bank's directory and shouldn't pretend to be.
- `CopVerdictService` is the deep module: it owns the entire verdict path (local lookup → optional federation → name-match rules → anti-fishing threshold). Existing `ConfirmationOfPayeeService` collapses into it or delegates to it; either way, the verdict logic lives in one place.

The `0.85` anti-fishing threshold from ADR-0001 applies on **both** the local and federated paths — the stub re-implements the same comparison logic, *not* delegates string comparison to the caller, so the threshold is enforced server-side wherever the directory lookup happens. The anti-fishing residual limitation noted in root ADR-0001 carries over unchanged.

The same `StubFictionalDirectory` is the source of truth for future FPS-outbound destinations (#04) and the originator names used by the demo inbound endpoint (#06). One directory, multiple uses.

Domain glossary terms: `Confirmation of Payee`, `Stub`, `Bank's directory` (now federated). Avoid "payee" when speaking of CoP — the directory question is bank-scoped, not holder-scoped.

## Acceptance criteria

- [ ] New Maven modules registered: `malai-clients/malai-stub-client` + `malai-clients/malai-stub-client-test-support`. `account-service` depends on `malai-stub-client`.
- [ ] `malai-stub` exposes `POST /cop/lookup` with request `{ sortCode, accountNumber, name }` and response in the same shape as `/api/v1/confirmation-of-payee` (`verdict`, optional `onRecordName`).
- [ ] `StubFictionalDirectory` loaded from a static resource at startup — at minimum: 1 account per fictional bank with a clearly demo-friendly name. Directory contents are visible in a single file a demonstrator can edit.
- [ ] `CopVerdictService` in `account-service`: local hit → ADR-0001 logic returns verdict; local miss → HTTP call to `malai-stub-client` → verdict from stub returns through unchanged. No double-counting (a local `MATCH` does not also trigger a stub call).
- [ ] `MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85` applies on both paths. Pair-not-found returns `NO_MATCH` indistinguishably from "pair found, name too different" — no fishing signal leaks.
- [ ] Acceptance tests in `account-service` cover: local hit unchanged (regression-free), local miss + stub hit, local miss + stub miss, name-typo on stub path returns `CLOSE_MATCH` with `onRecordName`, name-mismatch on stub path returns `NO_MATCH` with no `onRecordName` leak, anti-fishing threshold enforced on stub path.
- [ ] `malai-stub-client-test-support` provides a `WithMalaiStub` interface; existing `account-service` tests that hit a non-malai destination use it to stub the federated call.
- [ ] `malai-stub`'s own tests cover the `/cop/lookup` endpoint: known-pair-name-match returns `MATCH`, known-pair-name-typo returns `CLOSE_MATCH`, unknown-pair returns `NO_MATCH`.
- [ ] `openapi.yaml` unchanged — verdict shape preserved end-to-end.
- [ ] `mvn install` passes.

## Blocked by

`.scratch/send-and-receive-money/issues/02-fps-outbound-happy-path-malai-to-malai.md`

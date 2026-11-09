# 01 — Payments settled-window internal read + client support

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The fact source for post-event monitoring. payments-service exposes a new internal service-JWT read that returns settled payments within a `[from, to)` window across all accounts, carrying the fields the typologies consume: payment id, rail, direction, state, debtor and beneficiary identity (account id, sort code + account number, IBAN, BIC, name), amount and currency, created-at, and end-to-end id. The payments client gains the matching method, and its test-support module gains the request builders and response builders other modules will stub with.

Internal routes stay out of the OpenAPI contract, by the contract's own stated rule. The endpoint follows the existing internal-endpoint pattern: internal-scope guard, service-JWT only.

## Acceptance criteria

- [ ] Internal endpoint returns settled payments in the window, and only settled ones — other lifecycle states are excluded
- [ ] Endpoint rejects callers without the internal scope
- [ ] Kensa acceptance tests (happy and sad) with strict response-payload matching
- [ ] Client method with WireMock-backed tests covering request construction (method, URL, window params, headers) and response deserialisation
- [ ] Test-support builders for the settled-payments response, following the existing builder conventions
- [ ] `mvn install` green

## Blocked by

None - can start immediately

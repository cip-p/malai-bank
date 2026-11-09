# Confirmation of Payee resolves against the bank's account directory, not the holder's saved payees

`POST /api/v1/confirmation-of-payee` answers "does the bank recognise this (sort-code, account-number, name) triple?" — a name-check against the **bank's directory**, not against the caller's saved-beneficiaries list. Concretely, the lookup is against `account_identifiers` joined to `accounts`, filtered to `AccountType ∈ {PRIMARY, ADDITIONAL, SAVINGS, EXTERNAL}`, `OnboardingStatus = ACTIVE`, `IdentifierType = SORT_CODE`. `payees` stays purely "the holder's saved list" and is no longer consulted by Confirmation of Payee.

This is a domain-level distinction, not just a refactor. Real-world CoP is the *receiving bank's* answer about its own customer ledger, so a sender can name-check a destination *before* deciding to save it. Looking up `payees` inverted that — a destination was only resolvable after someone had already saved it, defeating the anti-typo / anti-APP-fraud purpose of CoP. In malai's single-bank fiction the bank's directory is just the `accounts` table, plus the V14 SYSTEM-owned `EXTERNAL` clearing accounts that stand in for "real accounts at other banks we happen to know about for demo purposes."

## Considered options

- **Move to `account-service`, top-level URL `/api/v1/confirmation-of-payee`** *(accepted)*. The lookup is structurally identical to `TransfersService.resolveDestination`; the endpoint isn't scoped to a specific account UID; the domain concept ("the bank's directory") is distinct from both saved-payees and individual account operations, so a top-level URL signals that better than nesting under `/api/v1/accounts/...`. OpenAPI tag: `ConfirmationOfPayee`.
- **Keep in `payee-service`, internal client call to `account-service`.** Argues that the *user-facing concept* is "name-check a payee before saving" so the endpoint lives in the payee mental model. Rejected: in the bundled `malai-api` JVM the internal hop is overhead with no benefit, and the bounded-context framing ("bank's directory") is the more honest one — payees are the *consequence* of a successful CoP, not its location.
- **New `directory-service` / new bounded context.** Cleanest domain modelling. Rejected as over-engineering for a demo; deferred unless / until the directory grows non-account-shaped entries (e.g. third-party PISP enrolments).

### Predicate

- **Stance Y — "bank-knows-this-account" semantics** *(accepted)*. CoP returns `MATCH` whenever the bank recognises the (sort, number) pair and the name fits, regardless of whether *this caller* could actually transfer there. Matches real CoP (which is unscoped on the sender), and pushes "you can't pay this" into transfer-time errors where it belongs. Future-proofs: when v2 enables customer-to-customer transfers, CoP doesn't need to change.
- **Stance X — "transfer-eligibility" semantics.** CoP returns `MATCH` only on destinations the caller can pay (their own accounts + `EXTERNAL`s). Rejected because it conflates two different questions (does the bank know this account? vs. is this caller allowed to pay it?) and bakes the v1 transfer policy into the directory schema.

The `AccountType` exclusion (`LOAN`, `FIXED_TERM_DEPOSIT`) is a property of the *account*, not of the caller — those accounts genuinely cannot receive ad-hoc payments, so they shouldn't appear in any payment-directory answer.

### Anti-fishing

`MIN_SIMILARITY_FOR_CLOSE_MATCH` raised from `0.5` to `0.85`. With the directory now covering every `ACTIVE` payment-target account in the bank — rather than the ~3 demo rows previously in `payees` — the leak vector matters as a policy, not just on today's row count. At `0.5`, an authenticated attacker who knows a victim's `(sort_code, account_number)` could recover the on-record name in 3-5 probes against `/api/v1/confirmation-of-payee` (the `CLOSE_MATCH` verdict returns `onRecordName`). At `0.85`, the probe has to come within ~1-2 edits of the real name, which is the *point* of typo tolerance, not enumeration. The webapp's three-verdict UX is unchanged because the *bucket* is unchanged — only the threshold moves.

Residual limitation: a sufficiently determined attacker with prior knowledge of the victim's name shape can still walk the threshold. The proper fix is bucket-based `CLOSE_MATCH` that doesn't return `onRecordName` at all (matches Pay.UK's "show me, I'll verify out-of-band" model), but that requires webapp UX changes which are out of scope here. Captured as a known demo-grade limitation; revisit before any production-grade deployment.

## Consequences

- `/api/v1/payees/confirmation-of-payee` is removed (hard cutover, no deprecation shim). The webapp's existing CoP form continues to work after `npm run openapi:gen` because request/response schemas are byte-identical.
- `oasdiff` will flag the path removal as a breaking change in CI — this is the correct signal; we own both sides of the contract.
- `PayeesRepository.findFirstBySortCodeAndAccountNumber` is deleted. Anything that needs the same lookup in the future calls `AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier` — the canonical "find an account by sort+number" path that `TransfersService` already uses.
- `IdempotencyConfiguration.protectedPaths` swaps the old path for the new one. The endpoint remains idempotency-protected and `@NonMutatingEndpoint`, satisfying the existing ArchUnit invariants on the account-service side without change.
- A buyer evaluating CoP gets the right answer: malai's CoP resolves against the bank's directory, exactly as a production-grade UK bank would. The closed-set deterministic mock posture is preserved — no Pay.UK network integration is attempted.

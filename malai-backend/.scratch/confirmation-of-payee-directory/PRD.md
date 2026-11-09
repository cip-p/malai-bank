# PRD: Confirmation of Payee resolves against the bank's account directory

Status: ready-for-agent

## Problem Statement

A malai customer at `/move/payees/new` enters a recipient's sort-code, account-number, and name to add a new payee. The form calls `POST /api/v1/payees/confirmation-of-payee` to name-check the destination before saving — and almost always gets back `NO_MATCH`, even for seeded external recipients the bank obviously knows about (ACME Employer Ltd, Tesco, TfL, Octopus, Spotify, Demo Payee 1). The Confirmation of Payee (CoP) feature looks broken to a buyer watching the demo.

The underlying flaw is that today's CoP resolves against the wrong directory. `ConfirmationOfPayeeService.confirm` queries the `payees` table — the *sender's-side saved-beneficiaries list*. In real-world CoP, the receiving bank answers from **its own customer ledger**, so the sender can name-check a destination *before* deciding to save it. Looking up `payees` inverts that: a destination is only CoP-resolvable after someone has already saved it, defeating the anti-typo / anti-APP-fraud purpose of CoP.

Concrete symptom: `POST /api/v1/payees/confirmation-of-payee` with `{"name":"ACME Employer Ltd","sortCode":"65-82-10","accountNumber":"10000000"}` returns `NO_MATCH` despite the account existing in `accounts` + `account_identifiers` (seeded by `V14__seed_external_clearing_accounts.sql`). Only the three payees the Sarah-Mitchell scenario seeded into `payees` ever match, and they match for *any* signed-in user because the lookup is intentionally not holder-scoped.

## Solution

CoP resolves against the bank's account directory — the `accounts` table joined to `account_identifiers` — covering both internal customer accounts and the V14 SYSTEM-owned `EXTERNAL` clearing accounts that stand in for "real accounts at other banks we happen to know about for demo purposes." The lookup reuses the same path `TransfersService.resolveDestination` already uses (`AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier`).

The endpoint moves out of `payee-service` and into `account-service`, mounted at top-level `/api/v1/confirmation-of-payee` (the endpoint isn't scoped to a specific account UID, and the *concept* is the bank's directory rather than an account operation). The OpenAPI contract is a hard cutover under a new tag `ConfirmationOfPayee`, with request/response schemas byte-identical so the webapp's existing CoP form continues to work after `npm run openapi:gen`. `payees` stays purely "the holder's saved list" and is no longer consulted by CoP.

## User Stories

1. As a malai customer adding ACME Employer Ltd as a new payee from `/move/payees/new`, I want CoP to return `MATCH` against the seeded external clearing account, so that the form's three-verdict flow visibly works in the demo.

2. As a malai customer about to send a one-off payment, I want to name-check the (sort-code, account-number, name) triple *before* I save the payee, so that I catch typos before money moves.

3. As a malai customer who fat-fingers "Octopus Enrgy Ltd" against the correct sort-code + account-number, I want CoP to return `CLOSE_MATCH` with the on-record name "Octopus Energy Ltd", so I can self-correct without re-typing the whole pair.

4. As a malai customer who types a name wildly different from the on-record name (e.g. "Mickey Mouse" against ACME's pair), I want CoP to return `NO_MATCH` without revealing the real name, so that an authenticated attacker probing arbitrary pairs cannot harvest account-holder names.

5. As a malai customer name-checking a (sort-code, account-number) pair that doesn't exist in the directory at all, I want CoP to return `NO_MATCH` without distinguishing "pair not found" from "name didn't match", so an attacker cannot enumerate which pairs exist.

6. As a malai customer, I want CoP to recognise another customer's `PRIMARY` account when I name-check it (Stance Y, unscoped on holder), so the demo's "Bob name-checks Alice" scenario shows the directory working as a real CoP would. Whether Bob can actually *transfer* to Alice is the transfer endpoint's policy, not CoP's question.

7. As a malai customer name-checking a `LOAN` or `FIXED_TERM_DEPOSIT` account, I want CoP to return `NO_MATCH` because those account types cannot receive ad-hoc payments, so the directory's answer matches what the bank can actually accept.

8. As a malai customer name-checking an account with `OnboardingStatus = PENDING` (mid-registration), I want CoP to return `NO_MATCH` because the account isn't fully a real account yet.

9. As a malai customer name-checking using a non-UK identifier scheme (IBAN_BIC, ABA_ACH), I want CoP to return `NO_MATCH` because CoP is a UK-specific scheme — only `SORT_CODE` rows are considered.

10. As a buyer watching the demo, I want CoP to behave like real Pay.UK CoP — a name-check answered by the receiving bank, decoupled from the sender's saved-payees list — so the demo accurately represents what a production-grade UK retail bank would offer.

11. As a buyer evaluating malai's architecture, I want CoP's bounded context to be cleanly separated (its own controller/service in account-service, its own OpenAPI tag `ConfirmationOfPayee`), so the codebase signals where the directory concept lives.

12. As a buyer evaluating malai's security posture, I want a documented anti-fishing rule (`MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85`), so the demo's exposure to authenticated name-harvesting attacks is meaningfully limited.

13. As a developer working on malai-backend, I want the dead `PayeesRepository.findFirstBySortCodeAndAccountNumber` method removed, so the codebase doesn't carry a dangling lookup that points at the wrong table.

14. As a developer working on malai-backend, I want `IdempotencyConfiguration.protectedPaths` updated to the new path, so idempotency-replay semantics still apply.

15. As a developer working on malai-webapp-v1, I want the OpenAPI contract to be the single source of truth: change the path on the backend, regenerate the TS client, and the webapp's existing CoP form continues to work, so I don't need to chase parallel changes.

16. As a maintainer running CI, I want `oasdiff` to flag the path removal as a breaking change, confirming that the hard cutover is visible in CI signal, not silently elided.

17. As a maintainer reading the codebase six months from now, I want an ADR explaining why CoP lives under account-service and not payee-service, so I don't "fix" the structure by moving it back.

18. As a developer reading the relocated controller, I want `@AuthenticationPrincipal` narrowed to `MalaiUser` rather than `MalaiUserWithAccountHolderId`, so the injected type describes what the method actually depends on (Stance Y is unscoped, so the accountHolderId is genuinely unused).

19. As a developer running tests, I want the relocated acceptance tests to seed via `WithAccounts.anAccountExists()` + `anIdentifierExistsForAccount()` rather than `WithPayees.aPayeeExists()`, so the tests exercise the same path production code does.

## Implementation Decisions

**Module relocation.** The CoP controller, service, models (`ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResponse`, `ConfirmationOfPayeeResult`), and the `Levenshtein` utility move from `payee-service` to `account-service`. Their acceptance tests (happy + sad), the request builder, and `LevenshteinTest` move alongside. The `payee-service` versions are deleted.

**Endpoint.** New top-level URL: `POST /api/v1/confirmation-of-payee` (no account-UID in the path; the endpoint is bank-directory-wide, not per-account). The old `/api/v1/payees/confirmation-of-payee` is removed — hard cutover, no deprecation shim.

**Bounded-context framing.** The endpoint represents "the bank's name-check directory", distinct from both saved-payees (the holder's beneficiary list) and account operations (CRUD on a specific account UID). This framing is captured in the ADR; it dictates the top-level URL, the new OpenAPI tag `ConfirmationOfPayee`, and the controller's location in `account-service`.

**Predicate (Stance Y — "bank-knows-this-account" semantics).** CoP is unscoped on the calling holder. The lookup:

```sql
SELECT a.* FROM account_identifiers ai
JOIN accounts a ON ai.account_id = a.account_id
WHERE ai.identifier_type    = 'SORT_CODE'
  AND ai.bank_identifier    = :sortCode
  AND ai.account_identifier = :accountNumber
  AND a.status              = 'ACTIVE'
  AND a.account_type IN ('PRIMARY','ADDITIONAL','SAVINGS','EXTERNAL')
LIMIT 1;
```

In code, this reuses `AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier` (the same path `TransfersService.resolveDestination` already uses) and filters the resolved account by `OnboardingStatus.ACTIVE` and `AccountType ∈ {PRIMARY, ADDITIONAL, SAVINGS, EXTERNAL}`. `LOAN` and `FIXED_TERM_DEPOSIT` are excluded because they cannot receive ad-hoc payments.

**Verdict logic (unchanged shape, threshold tightened).**

- Levenshtein distance ≤ 2 → `MATCH`
- Similarity ≥ `MIN_SIMILARITY_FOR_CLOSE_MATCH` → `CLOSE_MATCH` with `onRecordName`
- Pair found, name too dissimilar → `NO_MATCH` (no `onRecordName` leaked — anti-fishing)
- Pair not found → `NO_MATCH`

`MIN_SIMILARITY_FOR_CLOSE_MATCH` is raised from `0.5` to **`0.85`** to harden the anti-fishing posture now that the directory surface is bank-wide rather than the ~3 demo `payees` rows it was sized for.

**API contract.** `openapi.yaml` updated as part of this work (CLAUDE.md invariant — edit `openapi.yaml` first). Schemas `ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResult`, `ConfirmationOfPayeeResponse` stay byte-identical. The endpoint moves under a new tag `ConfirmationOfPayee` (distinct from both `Payees` and `Accounts`, mirroring the bounded-context distinction). `oasdiff` will flag the path removal as breaking; that is the correct signal.

**Decorations preserved.** `@PreAuthorize("hasRole('CUSTOMER')")`, `@NonMutatingEndpoint`, idempotency-protected POST. The `mutatingControllersReachAuditWriter` ArchUnit rule from `malai-archunit-rules` is applied identically by account-service's `AccountServiceArchitectureTest` and will transparently honour `@NonMutatingEndpoint`. No new ArchUnit work.

**Auth principal narrowed.** Controller's `@AuthenticationPrincipal` injection changes from `MalaiUserWithAccountHolderId` to `MalaiUser`. The narrower type was a residual of Stance-X-style scoping that Stance Y doesn't need; the `@Suppress("unused")` annotation already signalled this.

**Cleanup.**

- `PayeesRepository.findFirstBySortCodeAndAccountNumber` is deleted (sole consumer was the old CoP service).
- `IdempotencyConfiguration.protectedPaths` entry swaps `/api/v1/payees/confirmation-of-payee` for `/api/v1/confirmation-of-payee`.
- The stale comment in `malai-end-to-end-tests/.../IdempotencyTest.kt` referencing the old path is updated.

**ADR.** `malai-backend/docs/adr/0001-confirmation-of-payee-resolves-against-bank-directory-not-saved-payees.md` (already written) captures the bounded-context reasoning, the rejected predicate stance (Stance X), the anti-fishing threshold change, the residual leak limitation, and the cleanup consequences. The ADR is binding context for the implementation; it establishes the new `malai-backend/docs/adr/` folder.

## Testing Decisions

**What makes a good test here.** Assert external behaviour: HTTP request in → HTTP response verdict out → database seed state. No mocking of internal lookup methods. Full-entity database assertions where state is asserted (matches the CLAUDE.md pattern). Kacey response assertions with `noExtraProps()` at every level so a new field on the response surface forces tests to update.

**Modules under test (acceptance level):**

- `ConfirmationOfPayeeController` (account-service) — happy and sad paths exercised end-to-end through the running Spring context, hitting the real database. Tests extend `AccountsAcceptanceTest`, inheriting `WithAccounts` for seeding and `WithTestUuidGenerator` for deterministic IDs.
- `Levenshtein` — pure unit-level coverage, relocated unchanged from payee-service.

**Acceptance test migration.**

- `WithPayees.aPayeeExists(...)` calls in the relocated CoP tests get rewritten to `WithAccounts.anAccountExists(...)` + `WithAccounts.anIdentifierExistsForAccount(...)`, seeding the same conceptual destination via the production-correct path.
- CLOSE_MATCH fixtures get re-tuned for the new `0.85` threshold (existing 0.5-tuned probe names will mostly become `NO_MATCH` and need replacing with closer-to-real-name probes).

**Prior art in this repo.**

- `AccountsControllerTest` (happy + sad) — the canonical account-service acceptance pattern: Kensa BDD `given() / when() / then() / and()`, Kacey `responseEntity().containsProp(json { ... noExtraProps() })`, seeding via `WithAccounts`.
- `TransfersControllerTest` — exercises the same `AccountIdentifierRepository` lookup path that the new CoP service reuses; reference for sort-code/account-number fixture setup.
- The existing `ConfirmationOfPayeeControllerTest` in `payee-service/src/test/...` — the source of truth for the three-verdict happy/sad coverage matrix; structure transplants, fixture seeding rewrites.

**Out-of-scope for testing within this PRD** (per Q7 of the grilling): new explicit acceptance cases for `LOAN`/`FIXED_TERM_DEPOSIT`/`PENDING` exclusions and cross-tenant Stance Y behaviour. The relocated tests + retuned CLOSE_MATCH cases provide sufficient coverage; the predicate filter is small enough that a future regression would surface in any holistic test pass.

## Out of Scope

- **Webapp `/move/payees/new` UI changes.** The form already calls CoP and handles the three verdicts. Only the underlying API path moves; the webapp picks it up via `npm run openapi:gen`.
- **`payees` table schema or `PayeesService` CRUD behaviour.** `payees` stays purely "the holder's saved list."
- **Real Pay.UK CoP network integration.** Malai remains a closed-set deterministic mock.
- **Bucket-based `CLOSE_MATCH`** (returning the verdict without `onRecordName`, matching Pay.UK's "show me, I'll verify out-of-band" model). Would require webapp UX changes; captured in the ADR as a known demo-grade limitation, revisit before any production-grade deployment.
- **`CONTEXT.md` / `CONTEXT-MAP.md`.** Punted; the ADR carries the term clarity for now. Start when 5+ terms have accumulated.
- **New `directory-service` bounded context.** Over-engineering for a demo; deferred unless / until the directory grows non-account-shaped entries.

## Further Notes

- The ADR at `malai-backend/docs/adr/0001-confirmation-of-payee-resolves-against-bank-directory-not-saved-payees.md` is binding context for the implementation. Read it first.
- CI's `oasdiff` will flag the OpenAPI path removal as a breaking change. This is the correct signal; we own both sides of the contract (backend + webapp ship together).
- `mvn install` is the verification gate. Per CLAUDE.md: when stale `target/` causes Kensa "Did not find declaration for class..." errors, wipe and rebuild with `mvn clean install`.
- Both services use `FreezingArchRule.freeze(...)` baselines for `mutatingControllersReachAuditWriter`. The frozen state may need a refresh on first build after the move — handled mechanically by the build.
- The lookup reuses `AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier`; do not duplicate the JPQL or add a new repository method. If a `findAccountForCoP(...)` higher-level helper is wanted, locate it on the new `ConfirmationOfPayeeService` itself, not on the repository.
- The `(identifier_type, account_identifier)` UNIQUE constraint from V3 means at most one row matches any (sort, number) probe; the verdict logic doesn't need to handle ambiguity.

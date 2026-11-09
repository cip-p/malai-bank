# Relocate Confirmation of Payee to account-service with Stance Y predicate and hard-cutover URL

Status: done

## Parent

`.scratch/confirmation-of-payee-directory/PRD.md`

## What to build

End-to-end relocation of Confirmation of Payee from `payee-service` to `account-service`, including the hard-cutover URL change, the Stance Y predicate, the OpenAPI contract update, the idempotency-allowlist swap, the auth-principal narrowing, and the dead-code cleanup. After this slice ships, a malai customer calling `POST /api/v1/confirmation-of-payee` with `{"name":"ACME Employer Ltd","sortCode":"65-82-10","accountNumber":"<seeded>"}` gets `MATCH`. The old `/api/v1/payees/confirmation-of-payee` returns 404. The webapp's existing `/move/payees/new` form continues to work after `npm run openapi:gen`.

`MIN_SIMILARITY_FOR_CLOSE_MATCH` stays at the legacy `0.5` for this slice — the bump to `0.85` lands in the follow-up issue to keep CLOSE_MATCH fixture churn contained.

**Predicate (Stance Y).** Lookup reuses `AccountIdentifierRepository.findByIdentifierTypeAndBankIdentifierAndAccountIdentifier` — the same path `TransfersService.resolveDestination` already uses. Filters:

- `IdentifierType = SORT_CODE` (CoP is UK-only)
- Resolved account must have `OnboardingStatus = ACTIVE`
- Resolved account must have `AccountType ∈ {PRIMARY, ADDITIONAL, SAVINGS, EXTERNAL}` — excludes `LOAN` and `FIXED_TERM_DEPOSIT` because those cannot receive ad-hoc payments
- Unscoped on holder — a cross-tenant name-check (Bob name-checks Alice's PRIMARY) returns `MATCH` if the name fits; transfer-time policy is a separate concern

**Verdict shape unchanged.** Levenshtein distance ≤ 2 → `MATCH`; similarity ≥ `MIN_SIMILARITY_FOR_CLOSE_MATCH` → `CLOSE_MATCH` with `onRecordName`; pair found but name too dissimilar → `NO_MATCH` (no `onRecordName` leaked — anti-fishing); pair not found → `NO_MATCH`.

**OpenAPI hard cutover.** Remove the `/api/v1/payees/confirmation-of-payee` path entry. Add `/api/v1/confirmation-of-payee` under new tag `ConfirmationOfPayee` (distinct from `Payees` and `Accounts`). Schemas `ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResult`, `ConfirmationOfPayeeResponse` stay byte-identical so the webapp's CoP form is unaffected after regen. `oasdiff` will flag the path removal as breaking — that is the correct CI signal; we own both sides.

**Module move from `payee-service` to `account-service`.**

- `controller/ConfirmationOfPayeeController` (mounted at `/api/v1/confirmation-of-payee`, top-level)
- `service/ConfirmationOfPayeeService`
- `model/ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResponse`, `ConfirmationOfPayeeResult`
- `util/Levenshtein` and its `LevenshteinTest`
- Acceptance tests (happy and sad) — relocated, extending `AccountsAcceptanceTest` to inherit `WithAccounts` + `WithTestUuidGenerator`
- `builders/request/ConfirmationOfPayeeRequestBuilder`

The relocated acceptance tests rewrite seeding from `WithPayees.aPayeeExists(...)` to `WithAccounts.anAccountExists(...)` + `WithAccounts.anIdentifierExistsForAccount(...)`, exercising the same path production code does. The CLOSE_MATCH probe names in the existing fixtures continue to work at the legacy `0.5` threshold for this slice.

**Decorations preserved.** `@PreAuthorize("hasRole('CUSTOMER')")`, `@NonMutatingEndpoint`, idempotency-protected POST. Controller's `@AuthenticationPrincipal` narrows from `MalaiUserWithAccountHolderId` to `MalaiUser` (Stance Y doesn't use `accountHolderId`); the existing `@Suppress("unused")` annotation is removed.

**Cleanup landing in this slice.**

- Delete `PayeesRepository.findFirstBySortCodeAndAccountNumber` — its sole caller was the old CoP service, which is being deleted here.
- Swap `IdempotencyConfiguration.protectedPaths`: remove `/api/v1/payees/confirmation-of-payee`, add `/api/v1/confirmation-of-payee`.
- Update the stale comment in `malai-end-to-end-tests/.../IdempotencyTest.kt` referencing the old path.
- Delete the old payee-service CoP files (controller, service, models, Levenshtein, LevenshteinTest, acceptance tests happy + sad, request builder).

**Binding context.** `malai-backend/docs/adr/0001-confirmation-of-payee-resolves-against-bank-directory-not-saved-payees.md` captures the bounded-context reasoning, the rejected Stance X, the anti-fishing threshold change (handled in the follow-up), and the cleanup consequences. Read it before implementing.

## Acceptance criteria

- [ ] `POST /api/v1/confirmation-of-payee` with the V14-seeded `(name="ACME Employer Ltd", sortCode="65-82-10", accountNumber=<seeded>)` returns `MATCH`.
- [ ] Equivalent call against any of the other V14-seeded clearing accounts (Tesco, TfL, Octopus, Spotify, Demo Payee 1) returns `MATCH`.
- [ ] A call against a `(sortCode, accountNumber)` pair not present in `account_identifiers` returns `NO_MATCH` and the response does not contain `onRecordName`.
- [ ] A call against an account with `accountType ∈ {LOAN, FIXED_TERM_DEPOSIT}` returns `NO_MATCH`.
- [ ] A call against an account with `status = PENDING` returns `NO_MATCH`.
- [ ] A call against a `(sortCode, accountNumber)` pair where the underlying `account_identifiers` row has `identifier_type != SORT_CODE` returns `NO_MATCH`.
- [ ] A cross-tenant name-check (caller is holder A, target is holder B's `PRIMARY`) returns `MATCH` when the name fits — verifies Stance Y unscoped-on-holder behaviour.
- [ ] `POST /api/v1/payees/confirmation-of-payee` returns 404 (path removed).
- [ ] `openapi.yaml` contains no `/api/v1/payees/confirmation-of-payee` entry and has `/api/v1/confirmation-of-payee` under tag `ConfirmationOfPayee`. The `ConfirmationOfPayee` tag does not appear on any other path.
- [ ] Schemas `ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResult`, `ConfirmationOfPayeeResponse` are byte-identical to their pre-change definitions.
- [ ] `PayeesRepository.findFirstBySortCodeAndAccountNumber` is removed.
- [ ] `IdempotencyConfiguration.protectedPaths` contains `/api/v1/confirmation-of-payee` and does not contain `/api/v1/payees/confirmation-of-payee`.
- [ ] The following files are removed from `payee-service`: `ConfirmationOfPayeeController`, `ConfirmationOfPayeeService`, `ConfirmationOfPayeeRequest`, `ConfirmationOfPayeeResponse`, `ConfirmationOfPayeeResult` (and its containing file if dedicated), `Levenshtein`, `LevenshteinTest`, both `ConfirmationOfPayeeControllerTest` files (happy + sad), `ConfirmationOfPayeeRequestBuilder`.
- [ ] Equivalent files exist in `account-service` and are wired into the running Spring context.
- [ ] Relocated acceptance tests extend `AccountsAcceptanceTest` and seed via `WithAccounts.anAccountExists()` + `WithAccounts.anIdentifierExistsForAccount()` (no `WithPayees` references in the relocated tests).
- [ ] Controller `@AuthenticationPrincipal` is typed `MalaiUser` (not `MalaiUserWithAccountHolderId`), and no `@Suppress("unused")` annotation is on the principal.
- [ ] New controller carries `@PreAuthorize("hasRole('CUSTOMER')")` and `@NonMutatingEndpoint`.
- [ ] Stale comment in `malai-end-to-end-tests/.../IdempotencyTest.kt` referencing `/api/v1/payees/confirmation-of-payee` is updated to the new path.
- [ ] `MIN_SIMILARITY_FOR_CLOSE_MATCH` remains at `0.5` — the bump to `0.85` is the follow-up issue.
- [ ] `mvn install` passes from `malai-backend/` (full multi-module build, including ArchUnit rules and acceptance tests for both `account-service` and `payee-service`).

## Blocked by

None — can start immediately.

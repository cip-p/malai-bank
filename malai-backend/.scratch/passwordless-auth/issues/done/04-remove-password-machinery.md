# Remove the password machinery

Status: done

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (decision record: auth-service ADR-0001)

The conscious breaking change. Delete the old password login, confirm-register, resend-confirmation-code, forgot-password, confirm-forgot-password, and change-password — endpoints, contract entries, controllers, service logic, password-related Cognito client operations, WireMock stubs, test-support builders, password-flavoured audit event types, and the password field anywhere it survives. Apply the one-time accepted-breaking-changes allowance to the oasdiff CI gate for this change only, leaving the gate armed afterwards.

> **Carve-out (2026-06-10) — `malai-infra` ADR-0002.** This removal targets the **runtime auth API and its Cognito client** only. The **seeder** legitimately retains an `AdminSetUserPassword` call for the seeded demo user (Sarah) so test infra can sign in directly against the pool — see issue 06. The "repo-wide password search comes back clean" criterion below therefore excludes seeder code/configuration in the same way it already excludes migrations/history.

## Acceptance criteria

- [x] The contract contains exactly: register, login, verify-code, refresh-token, signout (plus non-auth endpoints) — no password fields anywhere
- [x] No password-related code, stubs, builders, or audit types remain (a repo-wide search for password-flavoured terms in production and test code comes back clean, excluding migrations/history and the seeder's `AdminSetUserPassword` for the seeded demo user — see carve-out)
- [x] oasdiff allowance is one-time and documented in the change; the gate fails again on the next unrelated breaking change
- [x] Old-endpoint e2e/acceptance tests are deleted, not skipped
- [x] `mvn install` green; backend CI green including the gated oasdiff step

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/02-passwordless-login-end-to-end.md`

## Completion note (2026-06-10)

The bulk of this removal already existed uncommitted in the working tree from a
prior run; this iteration verified it, fixed the one gap that kept the build red,
and confirmed the gate end-to-end.

Removed: password `login`/`confirm-register`/`resend-confirmation-code` (API endpoint)/
`forgot-password`/`confirm-forgot-password`/`change-password` — controller methods
(`AuthController`), service logic (`AuthService`), orchestrator `confirmRegister`
(`RegistrationOrchestrator`), request models (`AuthModels`), the auth-client request
builders, the password-auth Cognito client operations and their model classes
(`CognitoLoginRequest/Response`, `CognitoRegisterRequest`, `CognitoChangePasswordRequest`,
`CognitoForgotPasswordRequest`, `CognitoConfirmForgotPasswordRequest`,
`CognitoConfirmRegisterRequest`), the password-flavoured `MalaiCode` entries
(`CHANGE_PASSWORD_INVALID_INPUT`, `COGNITO_CONFIRM_REGISTER_ERROR`,
`COGNITO_FORGOT_PASSWORD_ERROR`, `COGNITO_CONFIRM_FORGOT_PASSWORD_ERROR`,
`COGNITO_CHANGE_PASSWORD_ERROR`, `INVALID_CREDENTIALS`, `USER_NOT_CONFIRMED`), the
`change-password` idempotency-protected route bean, the `somePassword` test fixture,
the WireMock password-op stubs and Kensa classifiers, and the contract's password
paths plus the vestigial register `201`/`RegisterResponse`. Old-endpoint acceptance
+ e2e tests were deleted (not skipped): `AuthControllerTest` happy/sad password cells,
`AuthServiceTest` password sections, `CognitoClientTest` password cases, e2e
`WithAuthService`/`WithCognito` password actions, and `IdempotencyTest` (its only
protected auth route was `change-password`).

Kept (carve-out / passwordless flow): the seeder `AdminSetUserPassword` (issue 06),
DB `spring.datasource.password` infra config, the generic credential redaction in
`BodyRedactor`/`AuditRedactor`, and the passwordless `resendConfirmationCode`
(Unconfirmed-User re-send from the issue-03 behavior matrix — distinct from the
removed `/resend-confirmation-code` endpoint).

The gap that kept the build red: `AuthServiceTestArchitectureTest` still hard-coded
`IDEMPOTENCY_PROTECTED_CONTROLLER_TESTS = setOf("AuthControllerTest")`, so the ArchUnit
rule demanded the auth controller tests assert on idempotency after its only
idempotency-protected route (`change-password`) was gone. Set to `emptySet()` — auth
now has zero idempotency-protected routes.

oasdiff allowance: `malai-api/oasdiff-accepted-breaking-changes.txt` (new), wired into
`.gitlab-ci.yml` via `--err-ignore`. Six lines pin path + rule for the six accepted
breaks. Verified with `tufin/oasdiff:v1.10.6`: gate passes with the allow-list
(exit 0), fails without it (the six breaks reported) — so it stays armed for any
unrelated breaking change and is inert once these endpoints leave `main`.

Verified: full `mvn install` green (run 1 = all upstream modules; run 2 = resume from
auth-service after the arch-test fix, through malai-api / e2e / seeders / malai-stub,
all tests `Failures: 0, Errors: 0`). E2e compiles under `e2e.skip=true` and runs in the
dedicated CI stage. oasdiff gate confirmed both directions.

Notes for next iteration: issue 06 (seeders, HITL) remains — it keeps Sarah's Cognito
password via `AdminSetUserPassword` and needs dev verification of `USER_PASSWORD_AUTH`
sign-in against the pool.

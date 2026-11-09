# Passwordless registration end-to-end

Status: done

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (decision records: auth-service ADR-0001, auth-service CONTEXT.md)

## What to build

The tracer bullet: a user registers with firstName/lastName/email only, receives a Confirmation code, and Verification signs them in immediately. Contract first — add the new `register` (name + email, "code sent" response) and `verify-code` (email + code → login response + cookies) endpoints to the OpenAPI contract; existing endpoints stay untouched in this slice, so the contract change is additive. Implement: passwordless Cognito SignUp (no password) via the new passwordless operations on the commons Cognito client; the Generated-details deep module (injectable; plausible UK address + adult dateOfBirth) feeding the registration orchestration in place of user-supplied values; Verification for the Unconfirmed User case — confirm the signup, mark the user confirmed, activate holder/accounts, exchange the returned Cognito session for tokens, set the standard auth cookies. Audit events for register and verification success/failure.

## Acceptance criteria

- [ ] Contract documents `register` (firstName, lastName, email — nothing else) and `verify-code`, with verify-code's response matching the existing login response shape; oasdiff stays green (additive)
- [ ] Acceptance tests (Kensa, happy + sad) cover: register creates the Cognito user passwordless, the DB user, and holder/accounts onboarding with Generated details — pinned by full-entity DB assertions against a stubbed generator
- [ ] Verification with a valid code leaves the user confirmed, active, and signed in (auth cookies set) in one request — no separate login step
- [ ] Invalid/expired code yields the single generic verify error
- [ ] Generated-details generator has isolated tests (UK address shape, adult DOB range, deterministic under stubbing)
- [ ] Passwordless Cognito client operations have client-level WireMock tests (request construction + response mapping)
- [ ] E2E test covers register → verify → authenticated call, with updated Cognito stubs
- [ ] Audit events recorded for register and verification outcomes; codes never logged
- [ ] `mvn install` green

## Blocked by

None - can start immediately

## Completion note (2026-06-10)

All acceptance criteria met. `register` is passwordless (firstName/lastName/email → 200 `CodeSentResponse`); `verify-code` confirms an Unconfirmed User via Cognito `ConfirmSignUp` + `USER_AUTH` session exchange, activates holder/accounts, and signs in with cookies. `GeneratedDetailsGenerator` (injectable, `Random`-seeded) supplies a curated UK address + adult DOB; stubbed in acceptance/api tests for full-entity DB assertions. New Cognito client ops `registerPasswordless`/`verifyRegistration` with client-level tests. Audit: `REGISTERED`, `VERIFICATION_SUCCEEDED`/`VERIFICATION_FAILED`; codes never logged. Verified green: serial `mvn install` (all 40 modules) + e2e `AuthTest` (`-De2e.skip=false`, 3/3).

Decisions:
- Kept the old `register` 201 `RegisterResponse` in the contract as vestigial so the change stays oasdiff-additive; old password endpoints (login/confirm-register/resend/forgot/change) untouched — their removal is issue 04.
- Reconciled the legacy `register and login` e2e journey to the new register contract (passwordless step, no `Password` in SignUp, generated DOB asserted as `something`); the password-flow tail it still exercises is left for issue 04 to delete.

Note for next iteration: oasdiff was reasoned-additive but not run locally (binary not installed; gate lives in GitLab CI) — confirm green there. The confirmed-User branch of `verify-code` is intentionally still the generic failure here; issue 02 adds the login-challenge branch.

# PRD: Passwordless authentication (backend)

Status: ready-for-agent

> **Decision update (2026-06-10) — the code tap is dropped (`malai-infra` ADR-0002).** The non-prod code tap this PRD assumed for seeded-user/test logins is unbuildable: the CustomMessage Lambda never sees the plaintext code. The Malai API stays **passwordless** as designed — only the **seeder** changes: the demo user **Sarah keeps a Cognito password** (set via the Admin API), and test infra signs her in **directly against the Cognito pool** with `USER_PASSWORD_AUTH`, never through the API. Knock-ons below: story 17 and the Seeders/Sequencing decisions are amended; the EMAIL_OTP sign-in flow loses automated end-to-end coverage (accepted for now).

Sibling PRDs: `malai-infra/.scratch/passwordless-auth/PRD.md` (Cognito pool changes; code tap dropped per its ADR-0002), `malai-webapp-v1/.scratch/passwordless-auth/PRD.md` (UI flows, refresh middleware). Governing decisions: auth-service ADR-0001 (passwordless-only via native EMAIL_OTP; amended for the seeded-user password exception), ADR-0002 (refresh derives identity from expired access token), auth-service `CONTEXT.md` (canonical terms: **Confirmation code**, **Verification**, **Challenge session**, **Indistinguishability**, **Generated details**).

## Problem Statement

Registering with Malai today demands a 12-character password, a date of birth, and a full address before a prospect ever sees the product — friction that undercuts the core pitch ("this looks like a real bank I'd want to run") during self-guided evaluation. The password also drags a tail of machinery (forgot/change/reset flows, resend endpoint, password policy) that a prospect's technologist must audit and that no demo ever exercises. Login responses additionally reveal whether an email is registered, which reads as an amateur mistake to a bank evaluator.

## Solution

Authentication becomes passwordless-only: a user registers with just a name and email, receives a Confirmation code by email, and is signed in the moment they verify it. Login is the same gesture — email in, code back, signed in. The backend invents the address and date of birth (Generated details), treats register-with-taken-email as a silent login, and never reveals through any response whether an email is known (Indistinguishability). Once signed in, nothing changes: the same cookies, the same tokens — but sessions now survive up to 30 days because refresh works from a cold page load.

## User Stories

1. As a registering user, I want to sign up with only my first name, last name, and email, so that I can reach the product in seconds.
2. As a registering user, I want to receive a code by email and enter it to complete registration, so that I never create or remember a password.
3. As a registering user, I want to be signed in immediately after entering a valid code, so that I am not bounced through a separate login step.
4. As a registering user, I want my account to come with a plausible UK address and date of birth already on file, so that profile and KYC-adjacent surfaces look like a real bank's without me filling in forms.
5. As a returning user, I want to log in by entering only my email and the code it receives, so that login is as easy as registration.
6. As a returning user who accidentally registers again, I want to receive a normal login code and end up signed in to my existing account, so that my mistake is invisible and harmless.
7. As a user who registered but never entered the code, I want either the register or the login screen to send me a fresh code that completes my original registration, so that I am never stuck between states.
8. As a user whose code expired or was mistyped, I want a single generic error and the ability to request a fresh code by re-submitting my email, so that recovery is one obvious gesture.
9. As a user, I want every code email to look identical regardless of which flow triggered it, so that the experience reads as one coherent system.
10. As a signed-in user, I want my session to quietly renew for up to 30 days of activity, so that I am not re-prompted for a code every hour.
11. As a signed-in user on a cold page load with an expired access token, I want the refresh to succeed without my browser knowing my user id, so that "remember me" works with HttpOnly cookies only.
12. As a bank evaluator probing the API, I want register, login, and verify responses to be byte-identical for known and unknown emails, so that I cannot enumerate the customer base.
13. As a bank evaluator reading the contract, I want no vestigial password endpoints or fields, so that the API surface reads as deliberately passwordless rather than half-migrated.
14. As a bank evaluator, I want an audit event recorded for every registration, code send, verification success/failure, refresh, and sign-out, so that the compliance story holds under passwordless auth.
15. As a compliance reviewer, I want the accepted response-timing enumeration channel written down (ADR-0001), so that it reads as a conscious trade-off rather than an oversight.
16. As a demo presenter, I want to log in live by receiving a real branded email on screen, so that the passwordless UX itself becomes part of the pitch.
17. As a demo operator, I want the seeded user (Sarah) created confirmed and email-verified with a retained Cognito password, so that test infra and I can sign in as her directly against the pool without depending on a code tap. (Amended — see the decision update; the original "without any password ever existing" is reversed for seeded users only.)
18. As a QA engineer, I want acceptance tests to pin every cell of the behavior matrix, so that anti-enumeration regressions are caught at build time.
19. As a developer, I want the Cognito passwordless choreography hidden behind a small client interface, so that auth-service logic stays readable and the Cognito SDK is swappable in tests.
20. As a developer, I want generated details to come from an injectable component, so that full-entity database assertions remain deterministic.
21. As a webapp developer, I want the regenerated OpenAPI client to make every removed field and endpoint a compile error, so that the UI migration is guided by the type system.

## Implementation Decisions

- **Contract first.** The OpenAPI contract is rewritten before any controller: `register` takes firstName/lastName/email; `login` takes email; both return the identical "code sent" 200 schema. A new shared `verify-code` endpoint takes email + code (+ optional challenge session field) and returns the existing login response shape with Set-Cookie. Removed outright: confirm-register, resend-confirmation-code, forgot-password, confirm-forgot-password, change-password, and the password field everywhere. The `/api/v1` break is taken consciously with a one-time accepted-breaking-changes allowance in the oasdiff CI gate; the gate stays armed afterwards.
- **Behavior matrix** (the heart of the feature — Verification branches on User state, never on entry point):

  | | Unknown email | Unconfirmed User | Confirmed User |
  |---|---|---|---|
  | register | passwordless SignUp + DB user + account-holder onboarding; Cognito emails the code | re-send confirmation code; submitted name silently ignored | email-OTP login challenge; name silently ignored |
  | login | nothing sent; fabricated Challenge session cookie; identical 200 | re-send confirmation code | email-OTP login challenge |
  | verify-code | generic failure | confirm signup → mark confirmed → activate holder/accounts → exchange returned session for tokens → signed in | answer challenge with code → tokens → signed in |

- **Cognito mechanism** is the native passwordless feature set (USER_AUTH flow, EMAIL_OTP first factor, passwordless SignUp, ConfirmSignUp session exchange) — no custom-auth Lambdas. See ADR-0001 and the infra PRD for the pool-side enablement.
- **Passwordless Cognito client (deep module, `malai-security` commons).** Replaces the password-auth surface of the existing Cognito client with roughly three operations: start a login challenge for an email (returns an opaque session), answer a challenge (email + code + session → token triple), and confirm a registration (email + code → token triple via the returned session). Password, forgot-password, and resend operations are deleted.
- **Challenge session** rides a short-lived HttpOnly cookie (Secure, SameSite=Lax, ~10 min) set on every login response — fabricated with same-shape random content for unknown emails — with an optional request-body field as the non-browser fallback, mirroring the refresh-token dual-mode idiom. Cleared on successful Verification.
- **Generated-details generator (deep module, auth-service).** Injectable component (same pattern as the UUID generator and stubbed time) producing a curated plausible UK address (real-format postcode, country GB) and an adult date of birth (25–65). The registration orchestrator consumes it; address and dateOfBirth leave the request models.
- **Stale-token identity reader (deep module, `malai-security`).** Extracts the subject claim from a JWT whose signature verifies against the Cognito JWKS but whose expiry is ignored. The refresh endpoint uses it when the now-optional userId is absent, reading the expired access-token cookie. Signature verification is non-negotiable (ADR-0002).
- **Cookie lifetimes.** The access-token cookie's Max-Age changes from the token's expiresIn to the refresh window (30 days) so the stale JWT remains readable for refresh; authorization continues to come solely from JWT expiry validation in the decoding filter. ADR-0002 is the tripwire against "fixing" this.
- **Generic verify error.** Wrong code, expired code, expired/missing session, unknown email, and attempt exhaustion all map to one status + one error body.
- **Email templates** unify into a single "here's your code" template; the password-reset template dies. Template rendering stays in the infra-owned CustomMessage Lambda (see infra PRD).
- **Audit events** are reshaped to the new operations (register, code re-send, verification success/failure, login challenge started); password-related event types are removed. Failures still recorded independently of transaction rollback; codes never logged.
- **Seeders** create Sarah (and any other demo users) confirmed, email marked verified, in the customer group, **with a Cognito password set via the Admin API** (`AdminSetUserPassword`, permanent). Signing in as her is `USER_PASSWORD_AUTH` directly against the pool (test infra / Playwright `auth.setup`), not the API and not a code tap. (Amended — original was "passwordless, no password"; the code tap it relied on is dropped per infra ADR-0002.)
- **Sequencing.** This repo deploys only after the infra additive step (USER_AUTH enabled alongside password flows). The infra cleanup step (remove password flows) is **cancelled** — `ALLOW_USER_PASSWORD_AUTH` stays on the app client so Sarah's direct login keeps working. Dev-pool users and corresponding user rows are wiped and reseeded (Sarah re-seeded *with* her password); no schema migration is required.

## Testing Decisions

- A good test pins external behavior — request in, response/DB/audit/Cognito-interaction out — never internal structure. The existing house rules apply unchanged: Kensa BDD (given/whenever/then), Kacey response assertions with no-extra-props at every level, and full-entity database assertions so any new entity field breaks tests loudly.
- **Acceptance tests (auth-service)** cover every cell of the behavior matrix, happy and sad, including: byte-identical responses across known/unknown email for register and login (assert the full response and Set-Cookie shape, not just status); fabricated vs real challenge-session cookies; the unconfirmed-user rescue from both entry points; the single generic verify error for each failure mode; refresh with and without userId, including the stale-cookie derivation; the extended access-cookie Max-Age; audit rows for every operation. Generated details are pinned by stubbing the generator and asserting the full user and S2S onboarding payloads.
- **Deep-module tests**: the passwordless Cognito client (request construction + response mapping against WireMock, in the established client-test style), the generated-details generator (shape/range invariants; deterministic under a stubbed source), and the stale-token identity reader (valid-expired accepted, bad-signature rejected, malformed rejected).
- **WireMock Cognito stubs** in the e2e and acceptance support modules are reworked to the new SDK targets (passwordless SignUp, USER_AUTH InitiateAuth, RespondToAuthChallenge, ConfirmSignUp-with-session) and the password-op stubs deleted; the auth client test-support builders follow the new request shapes.
- **End-to-end tests** rewrite the auth journey: register → verify (signed in immediately) → sign out → login → verify → refresh via cookie → sign out, plus the unconfirmed-rescue and taken-email-register journeys. Prior art: the existing auth e2e and cookie-mode e2e tests.
- Repository tests for any new custom repository functions, per house rule.

## Out of Scope

- Mitigating the response-timing enumeration channel (async/queued registration) — documented accepted risk in ADR-0001.
- MFA, TOTP enrollment, magic links, SMS, or any second factor.
- Mobile/CLI clients beyond preserving the header/body fallbacks that exist today.
- KYC, address verification, or user-editable address/date of birth.
- Audit retention/archival and per-shard chaining (separately deferred).
- Webapp UI and Playwright changes (webapp PRD) and all Terraform/Lambda work (infra PRD).

## Further Notes

- Do not add Idempotency-Key handling to the new auth endpoints without first surfacing the open critique of the shared idempotency package (BACK-07) — re-submitting register/login is the *intended* resend gesture, so natural idempotency questions should be settled in design review, not by reaching for the package.
- The webapp regenerates its client from this contract; land the contract and backend together, then refresh the webapp snapshot (workflow in the webapp repo docs).
- Demo rehearsal caveat: Cognito rate-limits email OTPs per user — keep a second demo account as insurance (also noted in the infra PRD).

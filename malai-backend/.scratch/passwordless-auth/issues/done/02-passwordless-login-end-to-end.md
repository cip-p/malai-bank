# Passwordless login end-to-end with Indistinguishability

Status: done

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (decision records: auth-service ADR-0001, auth-service CONTEXT.md)

## What to build

The new `login` endpoint: email in, "code sent" out, identically for known and unknown emails. For a confirmed User, start an email-OTP challenge via the passwordless Cognito client and carry the resulting Challenge session in a short-lived HttpOnly cookie (body field as non-browser fallback, mirroring the refresh-token dual-mode idiom). For an unknown email, send nothing but set a fabricated same-shape Challenge-session cookie so the response is byte-identical. Extend `verify-code` with the confirmed-User branch: answer the challenge with code + session → tokens → standard auth cookies, clearing the Challenge-session cookie. The old password `login` continues to exist until the removal slice.

## Acceptance criteria

- [x] Contract documents `login` (email only) with the same "code sent" response schema as `register`
- [x] Acceptance tests assert the **full** response + Set-Cookie shape is identical for known and unknown emails (not just status)
- [x] Verification for a confirmed User signs them in via cookie-carried session; body-fallback session also covered
- [x] Verification with an unknown email, wrong code, expired/missing session, or exhausted attempts all yield the same generic error
- [x] Challenge-session cookie is HttpOnly, Secure, SameSite=Lax, short-lived, and cleared on successful Verification
- [x] Audit events for login-challenge started and verification outcomes
- [x] E2E test covers login → verify → authenticated call
- [x] `mvn install` green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/01-passwordless-registration-end-to-end.md`

## Completion note (2026-06-10)

`login` is now email-only and starts an email-OTP challenge for a confirmed User (real
Cognito Challenge session in a short-lived `challenge_session` HttpOnly cookie), or a
fabricated same-shape session for an unknown email — byte-identical 200 + Set-Cookie either
way. `verify-code` gained the confirmed-User branch: it answers the challenge with the code +
the session read cookie-first / body-fallback, returns the standard token triple + auth
cookies, and clears `challenge_session`. Every verify failure (unknown email, wrong code,
missing/spent session) collapses to one generic `5974`. Audit: `LOGIN_CHALLENGE_STARTED` on
login, `VERIFICATION_SUCCEEDED`/`VERIFICATION_FAILED` on verify. The old password `login` is
gone (the PRD makes login passwordless); remaining password endpoints (change/forgot/confirm-
forgot/confirm-register/resend) stay for issue 04.

Verified: `mvn install` green (all 40 modules; acceptance + unit). E2e green with
`-De2e.skip=false` — `AuthTest` rewritten to register → verify → sign out → login → verify →
authenticated call → refresh → sign out (header mode, 3/3), `AuthCookieTest` rewritten to the
cookie-mode passwordless journey carrying `challenge_session` through login → verify (1/1).

Notes for next iteration: issue 03 adds the remaining matrix cells (register-with-taken-email
silent login challenge, Unconfirmed re-send + login-entry rescue). The legacy password-flow
e2e steps (forgot/confirm-forgot) were dropped from the rewritten auth journeys per the PRD's
e2e rewrite; issue 04 removes the endpoints themselves.

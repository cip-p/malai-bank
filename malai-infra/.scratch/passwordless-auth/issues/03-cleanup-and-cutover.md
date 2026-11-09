# Cleanup and cutover: remove password auth, wipe dev users

Status: wontfix

> **Cancelled (2026-06-10) — see [ADR-0002](../../../docs/adr/0002-retain-seeded-password-path-for-test-users-no-otp-tap.md).** This slice would have made the pool passwordless-only by removing password auth and wiping password-era users. The code tap (issue 02) proved unbuildable on the CustomMessage Lambda, so the seeded test user (Sarah) now *keeps* her email/password account as the test/demo login path while real users use EMAIL_OTP. Removing password auth would break that, so this slice is not actioned. The retained end-state — `sign_in_policy = [PASSWORD, EMAIL_OTP]`, app client keeping `ALLOW_USER_PASSWORD_AUTH` — is already what's deployed after issue 01. The criteria below are kept as the historical (cancelled) plan. (The reset-password template/branch is now effectively dead code — real users are passwordless and Sarah's password is seeded, never reset — but it is harmless and left in place; remove it only if it ever becomes worth the churn.)

## Parent

`malai-infra/.scratch/passwordless-auth/PRD.md`

## What to build

Phase three of the cutover. Remove USER_PASSWORD_AUTH and USER_SRP_AUTH from the app client (leaving USER_AUTH + REFRESH_TOKEN_AUTH), delete the reset-password email template and its Lambda branch, then run the operational steps: wipe the dev pool's password-era users and the corresponding backend user rows, and reseed (Sarah arrives passwordless via the backend seeder slice).

## Acceptance criteria

- [ ] App client allows only USER_AUTH and REFRESH_TOKEN_AUTH
- [ ] Reset-password template and its rendering branch are gone; Lambda tests updated
- [ ] Dev pool contains no password-era users; reseeded demo state works
- [ ] Full passwordless journey works in dev end-to-end: register → code → signed in; login as Sarah via the code tap
- [ ] Password login attempts against the pool fail at the Cognito layer
- [ ] `ci.sh` passes

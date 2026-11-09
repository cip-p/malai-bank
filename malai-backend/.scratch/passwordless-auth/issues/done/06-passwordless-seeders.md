# Seeders: retain Sarah's Cognito password for direct test login

Status: done

> **Amended (2026-06-10) — `malai-infra` ADR-0002.** Originally "passwordless seeders: no password material anywhere," relying on the infra code tap so seeded users could be signed in by emailed code. The code tap is unbuildable (the CustomMessage Lambda never sees the plaintext code), so seeded users instead **keep a Cognito password** and are signed in directly against the pool via `USER_PASSWORD_AUTH`. The runtime Malai API stays passwordless — this is a seeder-only, non-prod affordance.

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (decision records: auth-service ADR-0001 as amended, `malai-infra` ADR-0002)

## What to build

Both seeders create demo users (Sarah and any others) confirmed, email-verified, in the `customer` group, with the backend user row confirmed — and with a **Cognito password set via the Admin API** (`AdminSetUserPassword`, permanent). The seeder password is the only credential a test or a human uses to sign in as a seeded user (`USER_PASSWORD_AUTH` directly against the pool — the webapp Playwright `auth.setup` does this; see the webapp issue). Real users remain passwordless; this password never crosses the Malai API. Source the password from seeder configuration/secret, not a literal.

## Acceptance criteria

- [x] Seeders create users confirmed and email-verified in Cognito, confirmed in the DB, in the `customer` group, with a permanent Cognito password set via `AdminSetUserPassword`
- [x] The seeded password is read from seeder configuration/secret (not hard-coded) and is available to the webapp e2e setup the same way
- [x] Manual verification in dev: seed, then sign in as Sarah via `USER_PASSWORD_AUTH` `InitiateAuth` directly against the pool and receive a token triple
- [x] Seeder tests (matching existing seeder test conventions) updated
- [x] `mvn install` green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/01-passwordless-registration-end-to-end.md`

## Comments

### Note (2026-06-10)

The dependency on `malai-infra` issue 02 (code tap) is removed — that issue is `wontfix`. The only infra prerequisite is that the app client keeps `ALLOW_USER_PASSWORD_AUTH` (it does; infra ADR-0002 keeps it deliberately). The app client has a secret, so a direct `USER_PASSWORD_AUTH` `InitiateAuth` must supply a `SECRET_HASH` computed from the client id/secret — coordinate the exact mechanism with the webapp `auth.setup` slice. The pool runs advanced security `ENFORCED`, which may add adaptive-auth friction to a programmatic password sign-in (verification step below included); see infra ADR-0002 for the risk and fallbacks. The seeder's `AdminSetUserPassword` itself is unaffected — only the sign-in verification is.

# Playwright auth: sign in Sarah directly against Cognito (tap dropped)

Status: ready-for-human

> **Amended (2026-06-10) — `malai-infra` ADR-0002.** Originally "Playwright auth on the code tap": the setup project would poll the infra code-tap DynamoDB table for an emailed code. The code tap is unbuildable (the CustomMessage Lambda never sees the plaintext code), so the e2e auth foundation now signs in the seeded user (Sarah) by authenticating **directly against the Cognito pool** with her retained password — no tap, no code. The file keeps its number; the subject changed.

## Parent

`malai-webapp/.scratch/passwordless-auth/PRD.md` (decision record: `malai-infra` ADR-0002, superseding infra ADR-0001)

## What to build

Rework the e2e auth foundation for passwordless: the setup project signs in the seeded user (Sarah) by calling Cognito `InitiateAuth` with `USER_PASSWORD_AUTH` directly against the pool — username = Sarah's email, her seeded password, and a `SECRET_HASH` computed from the app-client id/secret (the client has a secret). Set the webapp's auth cookies from the result — `access_token` = AccessToken and `refresh_token` = RefreshToken (the only two cookies `AuthCookies`/`JwtDecoderFilter` read; `id_token` is unused, so it can be discarded) — and persist storage state exactly as today — single shared user, serial execution unchanged. The app-client id/secret come from the same Cognito secret the backend reads; Sarah's password from the seeder configuration. Stored password credentials for the _old API login_ disappear from e2e creds handling, but Sarah's Cognito password is now a legitimate test input.

Then handle the journey specs honestly: no emailed code is obtainable in tests, so the passwordless register/login/verify _flows_ cannot run end-to-end against dev. Cover those at the unit/integration level (mocked API) in the registration/login slices. The wrong-code generic error can still run e2e (a bogus code needs no real code). Everything that depends on _being signed in_ runs off the persisted storage state as before.

## Acceptance criteria

- [~] Auth setup signs Sarah in via `USER_PASSWORD_AUTH` directly against the pool and persists storage state; no old-API password-login creds remain in e2e handling — **implemented** (`auth.setup.ts` → `applyAuthCookies` → `signInViaCognito`; the deleted `/api/v1/auth/login` POST is gone, and no spec types a `Password` field anymore). The live InitiateAuth round-trip is HITL (needs dev Cognito + seeded password, backend issue 06).
- [x] The app-client id/secret and Sarah's password are sourced from config/secrets (not hard-coded), and `SECRET_HASH` is computed correctly — `loadCognitoClient` reads `mal-<env>-malai-api-cognito` (`client_id`/`client_secret`), `loadE2EUser` reads `mal-<env>-malai-api-end-to-end-test-user`; `computeSecretHash` is `base64(HMAC-SHA256(clientSecret, username+clientId))`, unit-tested against a known vector (`tests/support/cognitoSecretHash.test.ts`).
- [x] Auth setup fails loudly with a clear message when Cognito auth fails — `signInViaCognito` throws with HTTP status + the parsed Cognito body + the likely causes (wrong app-client secret, unseeded user, adaptive-auth challenge).
- [~] Authenticated journey specs pass … using the persisted storage state; wrong-code generic-error spec passes e2e — `verify-wrong-code.spec.ts` written (login → `/verify` → bogus `000000` → generic inline error, no toast; `/api/v1/auth/verify-code` 401 allow-listed). **Unrun AFK** — all of this is gated on a live stack + dev Cognito.
- [x] Register/login/verify flow coverage exists at unit/integration level (mocked API), and the docs note these are intentionally not full-e2e — covered by `LoginPage.test.tsx`, `RegisterPage.test.tsx`, `VerifyPage.test.tsx` (incl. the wrong-code generic error); CLAUDE.md Testing section now states the flows are intentionally not full-e2e.
- [ ] Full e2e suite and a11y suite green — gated on a live stack (HITL).
- [x] `npm run test:e2e` documented prerequisites updated (app-client secret + seeded password access; no tap) — CLAUDE.md "Auth setup" bullet + `playwright.config.ts` header updated: both Secrets Manager entries, env overrides, no tap.

## Blocked by

- `malai-webapp/.scratch/passwordless-auth/issues/02-webapp-login-flow.md`
- `malai-backend/.scratch/passwordless-auth/issues/06-passwordless-seeders.md` (Sarah must be seeded _with_ her Cognito password)

## Comments

### Note (2026-06-10)

The block on `malai-infra` issue 02 (code tap) is removed — that issue is `wontfix`. The only infra prerequisite is that the app client keeps `ALLOW_USER_PASSWORD_AUTH` (infra ADR-0002 keeps it deliberately for exactly this). Coordinate the `SECRET_HASH` computation and the seeded-password source with the backend seeder slice (issue 06).

Watch for adaptive auth: the pool runs advanced security `ENFORCED`, and a headless CI password login is the shape it flags — it may add risk friction or want `UserContextData`. Verify Sarah's direct login against dev before building the suite on it; fallback options are in infra ADR-0002. Once issued, the token is an ordinary Cognito access token the backend can't distinguish from an EMAIL_OTP one, so nothing downstream needs to change.

### Note (2026-06-10) — all webapp-side code landed; only the live-stack run is left

The e2e auth foundation is rebuilt and the broken old shape is gone. Status moved to `ready-for-human` because every remaining unticked box needs a live stack + dev Cognito (and Sarah seeded _with_ her password, backend issue 06) — there is no more agent-side code to write.

What changed:

- `tests/support/cognitoSecretHash.ts` (+`.test.ts`) — pure `computeSecretHash` = `base64(HMAC-SHA256(clientSecret, username+clientId))`, unit-tested against a precomputed vector. Lives under `tests/support/` (not `tests/e2e/`, which Vitest excludes) so `npm test` covers it; `tsconfig.node.json` now includes `tests/support/**` so `tsc -b` type-checks it.
- `tests/e2e/cognito.ts` — `signInViaCognito()` POSTs `InitiateAuth` / `USER_PASSWORD_AUTH` straight to `https://cognito-idp.<region>.amazonaws.com/` (an **unauthenticated** call — the app-client secret authenticates it, so no AWS SigV4/SDK needed; plain `fetch`). Throws loudly on non-OK / missing `AuthenticationResult` / challenge. `applyAuthCookies(context)` mints fresh tokens and injects `access_token`+`refresh_token` as host cookies mirroring the backend `AuthCookies` shape (HttpOnly, SameSite=Lax, path `/`, `secure` only over https, 30-day expiry; `id_token` discarded as unused).
- `tests/e2e/creds.ts` — added `loadCognitoClient()` (reads `mal-<env>-malai-api-cognito` → `client_id`/`client_secret`; env override `MALAI_COGNITO_CLIENT_ID/SECRET`); `loadE2EUser()` unchanged (Sarah's Cognito password is now a legitimate test input).
- `tests/e2e/auth.setup.ts` — rewritten: `browser.newContext()` → `applyAuthCookies` → `storageState`. No more `/api/v1/auth/login` POST (that endpoint is gone).
- Deleted `login-happy-path.spec.ts` and `login-wrong-password.spec.ts` (both drove the removed password field / old "Sign in to malai" copy; the journeys can't run e2e and are covered at unit level). Added `verify-wrong-code.spec.ts` for the one journey check that _can_ run e2e.
- `balance-visibility.spec.ts` — the post-signout re-login leg now re-mints a fresh Cognito session via `applyAuthCookies` + reload (the old UI password re-login is gone; GlobalSignOut invalidated the original refresh token, so a fresh `InitiateAuth` is required anyway).
- Docs: CLAUDE.md Testing section + `playwright.config.ts` header rewritten — Cognito-direct setup, both Secrets Manager entries, env overrides, no tap, and a "passwordless flows aren't full-e2e" note.

Feedback loops: `npm test` 718 passing (715 + 3 new secret-hash), `tsc -b` clean, eslint clean, prettier clean.

For the human (the only thing left): with dev Cognito reachable and Sarah seeded with her password, run `npm run test:e2e` + `npm run a11y` against the local stack. Verify (a) `auth.setup` mints a working session and the authenticated specs pass off `storageState`, and (b) `verify-wrong-code` reaches `/verify` and shows the generic inline error. If adaptive auth blocks the headless `USER_PASSWORD_AUTH` (advanced security `ENFORCED`), fall back per infra ADR-0002 — likely `AdminInitiateAuth` (`ADMIN_USER_PASSWORD_AUTH`) from a creds-bearing context, which would move the Cognito call off plain `fetch` onto the SDK/CLI.

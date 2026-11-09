# Webapp passwordless registration flow

Status: done

## Parent

`malai-webapp/.scratch/passwordless-auth/PRD.md` (decision records: webapp ADR-0001/0004, backend auth-service ADR-0001)

## What to build

Registration collapses to one screen — first name, last name, email — followed by the new shared Verification screen: a one-time-code input, the single generic error state, a resend affordance that re-submits the originating request, and on success a transition straight to the signed-in accounts landing (the user query turning authenticated is the only auth-state signal, per ADR-0001). Refresh the contract snapshot and regenerate the client first; the removed register fields make stale call sites compile errors. The address and credentials wizard steps disappear. Copy says "confirmation code", never "OTP".

## Acceptance criteria

- [x] Contract snapshot refreshed and client regenerated; validation comes from the generated Zod schemas (no hand-authored shadows)
- [x] Registering with name + email leads to the Verification screen; a valid code lands the user signed in on accounts with no separate login step
- [x] Generic error rendering and resend affordance covered by component tests
- [x] Address/credentials steps and their entry-guard logic are gone
- [x] Existing register-flow Playwright specs replaced to match the new shape (the full register→code e2e journey is dropped — no emailed code is obtainable in tests, infra ADR-0002; UI-level coverage here stubs the API per existing unit/integration conventions)
- [~] A11y spec sweeps the new register and Verification screens; axe passes — specs rewritten to sweep `/register` and `/verify`; the **run** is gated on a live stack and on issue 04 (the whole authenticated e2e suite can't authenticate until `auth.setup` is reworked to Cognito-direct). Not runnable AFK.
- [x] `npm test` (706 passing), lint, prettier, `tsc -b` green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/01-passwordless-registration-end-to-end.md` — landed on backend `main` (register / verify-code / login / refresh-token / signout; password machinery removed).

## What was done (2026-06-10)

Refreshed `./openapi.yaml` from the backend source of truth and regenerated the client (`npm run openapi:gen`). The regen is **atomic**: it simultaneously stripped `password`/`address`/`dateOfBirth` from `RegisterRequest`, reduced `LoginRequest` to `{ email }`, and deleted the `confirm-register`, `resend-confirmation-code`, `forgot-password`, `confirm-forgot-password`, and `change-password` endpoints. A green build was therefore impossible without also collapsing login to email-only and removing the password machinery — that forced minimum landed here; the login-specific polish remaining is noted on issue 02.

New surface (all validation from generated Zod schemas — no hand-authored shadows):

- `src/auth/verificationOrigin.ts` — discriminated `VerificationOrigin` (`register` | `login`) carried via router state so the Verification screen knows what to resend.
- `src/auth/useVerifyCode.ts` — `verify-code` mutation; on success invalidates the user query (the sole auth-state signal, ADR-0001) so the `Unauthenticated` guard redirects to `/`. `meta.handledLocally` so the wrong/expired-code 401 renders inline, not as a global toast.
- `src/auth/useRegister.ts` / `src/auth/useLogin.ts` — both now navigate to `/verify` carrying their origin. Login is email-only and indistinguishable (no wrong-credentials branch).
- `src/auth/useResendCode.ts` — re-submits the originating register/login request; toasts "We've sent a new confirmation code."
- `src/pages/RegisterPage.tsx` — single screen (first name, last name, email).
- `src/pages/VerifyPage.tsx` — shared Verification screen: OTP input, single generic error, resend affordance, redirect to `/login` if reached without an origin. Submit disabled until six digits.
- `src/pages/LoginPage.tsx` — email-only; `?email=` prefill retained; no password/forgot/help surfaces.

Deleted: `src/pages/register/*`, `ConfirmRegisterPage`, `ForgotPasswordPage`, `ConfirmForgotPasswordPage`, `SignInHelpPage`, `useConfirmRegister`, `useResendConfirmationCode`, `useForgotPassword`, `useConfirmForgotPassword`, `useChangePassword`, `RegisterStepIndicator`, `CountrySelect`, `DateOfBirthInput` (all orphaned by the regen), plus their tests, and the dead routes in `App.tsx`.

e2e: `register-skeleton.spec` rewritten to the single screen; `a11y.spec` sweeps `/register` + `/verify` and the deleted-route describes removed; `login-helper-links.spec` drops password/help links; `/login` heading copy updated across `redirect-unauthenticated`, `unauthenticated-layout`, `signout-round-trip`. The password-login specs (`login-happy-path`, `login-wrong-password`, the inline login in `balance-visibility`, and `auth.setup`/`creds`) are intentionally left for issue 04 — they depend on the auth foundation that issue 04 reworks to Cognito-direct, and the e2e suite can't run AFK.

Added a `typecheck` npm script (`tsc -b`).

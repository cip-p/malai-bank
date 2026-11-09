# Webapp passwordless login flow

Status: ready-for-human

## Parent

`malai-webapp/.scratch/passwordless-auth/PRD.md`

## What to build

Login becomes an email-only form feeding the same shared Verification screen built in the registration slice. All password surfaces go with the replaced pages: the password field, "Forgotten password?" and reset routes/links, password-strength UI, and related copy. Settings/profile renders email, date of birth, and address read-only — the latter two as Generated details. The unconfirmed-rescue and taken-email cases need no UI branches (the backend decides); the UI just keeps copy generic enough to be true in all cases.

## Acceptance criteria

- [x] Login asks only for email, then code; success lands on accounts
- [x] No password input, link, route, or copy remains anywhere (repo-wide search clean) — final sweep done now that issue 04 has landed and settled which strings are legitimate. Production code (`src/`, incl. `App.tsx` routes and the login/register pages) carries zero password/forgot/reset references. Every surviving `password` hit is one of three legitimate categories: (a) Cognito `USER_PASSWORD_AUTH` test inputs settled by issue 04 (`tests/e2e/cognito.ts`, `creds.ts`, `playwright.config.ts` header), (b) negative-guard assertions proving password surfaces are _gone_ (`LoginPage.test`, `RegisterPage.test`, `SettingsPage.test`, `login-helper-links.spec`, `register-skeleton.spec`), and (c) the generated SDK's stale `RefreshTokenRequest`/`ChangePassword*` types (`src/api/generated/*`, not hand-edited). The one stray _copy_ — `AuthShell.test.tsx`'s `description="Use your email and password."` fixture — is now a passwordless string. A literal "zero password strings" sweep was never the right end state (Sarah's Cognito password is a legitimate test input).
- [x] Settings shows email/DOB/address as immutable; no new edit affordances — `SettingsPage` ProfileCard now renders email, DOB, and the Generated address read-only in the same `<dl>` (each with "Contact support to change"); no inputs, no edit affordances. Address is the Holder profile's current address (`GET /api/v1/addresses`) via a new holder-scoped `useCurrentAddress` hook (mirrors `useIdentifiers`); displayed through `formatAddressLines`. Email + DOB were already read-only from issue 01; this slice added the address.
- [x] Component tests cover the email form and its reuse of the Verification screen (`LoginPage.test.tsx`, `VerifyPage.test.tsx`)
- [~] Login-related Playwright specs replaced to the new shape; helper-links spec drops password links — `login-helper-links` done; the login _journey_ specs depend on issue 04's Cognito-direct `auth.setup` and are left there.
- [~] A11y sweep covers the login email step; axe passes — `/login` describe heading updated; the **run** is gated on a live stack (see issue 04).
- [x] `npm test`, lint, prettier green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/02-passwordless-login-end-to-end.md`
- `malai-webapp/.scratch/passwordless-auth/issues/01-webapp-registration-flow.md`

## Comments

### Note (2026-06-10) — most of this landed inside issue 01

Refreshing the contract snapshot for issue 01 was an **atomic** breaking change: it deleted `LoginRequest.password` and every password endpoint at once, so login could not be left compiling in its old shape. The forced minimum therefore landed in issue 01's commit: `useLogin` is now email-only and navigates to the shared Verification screen, `LoginPage` is email-only (password field, "Forgotten password?" and `/forgot-password`/`/sign-in-help` routes, password-strength UI all removed), and the password hooks/pages were deleted.

**What remains for this issue:** the Settings/Profile read-only DOB + Generated-address display (the one untouched criterion), the live-stack a11y/Playwright runs (blocked on issue 04's auth foundation), and a final repo-wide copy sweep once issue 04 settles which password strings are legitimately test inputs.

### Note (2026-06-10) — Generated-address read-only display landed

The Settings read-only criterion is now done. `SettingsPage`'s ProfileCard renders the Generated **address** read-only alongside the email and DOB that issue 01 already surfaced. New pieces:

- `src/account-holder/useCurrentAddress.ts` — holder-scoped query for `GET /api/v1/addresses`, mirroring `useIdentifiers` (waits on `useActiveAccountHolder`, passes `MAL-Account-Holder-Id`, returns `data.current` or `null`). The address is the **Holder profile**'s, per CONTEXT.md "Generated details" — it is not on `UserResponse`.
- `src/account-holder/formatAddressLines.ts` — pure formatter, orders `line1/line2/line3/postTown/postCode`, drops blank optional lines, never emits the country code.
- `SettingsPage` renders the address row only when a current address exists (graceful while pending/absent), each line on its own `<span>`, with the same "Contact support to change" helper as email/DOB. No inputs, no edit affordances.

Coverage: `formatAddressLines.test.ts`, `useCurrentAddress.test.tsx`, and two new `SettingsPage.test.tsx` cases (read-only render + omitted-when-absent). `npm test` 715 green, typecheck/lint/prettier clean.

**Still remaining (all gated on issue 04's live-stack auth foundation):** the a11y/Playwright runs and the final repo-wide password-copy sweep. Issue stays open until those can run; not moved to `done/`.

### Note (2026-06-10) — final repo-wide password-copy sweep complete; status → ready-for-human

The copy sweep was the last agent-side deliverable on this issue, previously gated on issue 04 settling which password strings are legitimate test inputs. Issue 04 has landed (commit `4432add`): it deleted the old password-login specs (`login-happy-path`, `login-wrong-password`), reworked `balance-visibility`'s inline login onto Cognito, and rewrote `auth.setup`/`creds` for `USER_PASSWORD_AUTH`. So the "remaining hits" the earlier note deferred no longer exist as password surfaces.

A fresh repo-wide grep (`password`/`forgot-password`/`change-password`/`sign-in-help`/`password-strength`, excluding `node_modules`, `out/`, and `src/api/generated/`) shows the sweep is clean: the only non-generated `password` references are the three legitimate categories listed against the criterion above. The single stray _copy_ was an arbitrary test fixture in `AuthShell.test.tsx` (`description="Use your email and password."`) used only to assert the `description` prop renders — `AuthShell` wraps the now-passwordless login/register/verify pages, whose production descriptions are all passwordless ("Enter your email and we'll send you a confirmation code.", "Just your name and email to get started."). Updated the fixture (and its assertion) to the passwordless login description.

- `src/auth/AuthShell.test.tsx` — fixture + assertion changed from "Use your email and password." to "Enter your email and we'll send you a confirmation code."

Feedback loops: `npm test` 718 passing, `tsc -b` clean, eslint clean, prettier clean.

Status moved to **ready-for-human**: every remaining unticked box (`[~]` login _journey_ Playwright specs, `[~]` a11y axe sweep) is a live-stack **run**, not agent-side code — the specs and the `/login` a11y describe block already exist; they're gated on issue 04's live Cognito auth foundation (HITL). Not moved to `done/` — those runs are genuine remaining acceptance criteria.

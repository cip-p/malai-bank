# PRD: Passwordless authentication (webapp)

Status: ready-for-agent

> **Decision update (2026-06-10) — the code tap is dropped (`malai-infra` ADR-0002).** The non-prod code tap Playwright was going to poll for codes is unbuildable (the CustomMessage Lambda never sees the plaintext code). The **product UI is unchanged** — registration/login/verification stay fully passwordless. Only the **e2e auth foundation** changes: Playwright `auth.setup` signs in the seeded user (Sarah) by authenticating **directly against the Cognito pool** with her retained password (`USER_PASSWORD_AUTH`) and persisting storage state — no tap, no emailed code. Consequence: the passwordless register/login/verify _flow_ journeys can no longer run end-to-end against dev (no programmatic code access); they stay covered at the unit/integration level. Story 16 and the Playwright decisions below are amended.

Sibling PRDs: `malai-backend/.scratch/passwordless-auth/PRD.md` (contract, behavior matrix, cookies), `malai-infra/.scratch/passwordless-auth/PRD.md` (Cognito pool; code tap dropped per its ADR-0002). Governing decisions: backend auth-service ADR-0001 (as amended for the seeded-user password exception)/0002, `malai-infra` ADR-0002 (retain seeded password path; no tap), this repo's ADR-0001 (TanStack Query owns auth state), ADR-0004 (generated Zod as form-schema source), and `CONTEXT.md` (terms: **Registration**, **Login**, **Verification**, **Confirmation code**, **Generated details**, **Unconfirmed User**).

## Problem Statement

A prospect self-evaluating Malai today faces a three-step registration wizard demanding a 12-character password, a date of birth, and a full UK address before seeing a single account screen — and a login form whose password field implies a credential-management story nobody demos. Sessions also die after an hour because the webapp never exercises the refresh API, so a returning evaluator is logged out almost every time.

## Solution

Registration becomes one screen — first name, last name, email — followed by a code-entry screen; the user lands signed in on their accounts page straight from the code. Login is an email field and the same code screen. All password surfaces disappear. A refresh middleware quietly renews the session on expiry, so an evaluator who returns within 30 days walks straight back into the product.

## User Stories

1. As a registering user, I want a single registration screen asking only for my name and email, so that I reach the product in seconds.
2. As a registering user, I want to enter the emailed code in a purpose-built one-time-code input, so that the step feels like a modern banking app.
3. As a registering user, I want to land signed in on my accounts page immediately after a valid code, so that there is no second login ceremony.
4. As a returning user, I want the login page to ask only for my email and then the code, so that login and registration feel like one gesture.
5. As a user who mistypes or lets the code expire, I want one generic error with a "send a new code" affordance that re-submits my email, so that recovery is obvious.
6. As a user who registered but never confirmed, I want both register and login to route me into the same code screen and complete my registration, so that I am never stuck.
7. As a user re-registering with my existing email, I want the flow to look exactly like a fresh registration and end with me signed in to my existing account, so that nothing leaks and nothing breaks.
8. As a user, I want no "Forgotten password?" or password-strength UI anywhere, so that the product reads as deliberately passwordless.
9. As a signed-in user, I want my session to renew transparently when my token expires mid-use, so that in-flight screens never dump me to login.
10. As a signed-in user returning days later, I want a cold page load to restore my session from cookies alone, so that "remember me" just works.
11. As a signed-in user whose refresh genuinely fails, I want to be returned to the login screen cleanly, so that the failure mode is the old familiar one.
12. As a user viewing settings, I want my email, generated date of birth, and generated address shown read-only, so that the profile looks complete without pretending I can edit KYC data.
13. As a screen-reader user, I want the code-entry and email screens to pass the same a11y bar as the rest of the app, so that the accessibility story survives the redesign.
14. As a bank evaluator watching the network tab, I want register and login responses indistinguishable for known/unknown emails, so that the front end visibly cooperates with the anti-enumeration design.
15. As a developer, I want the regenerated API client and Zod schemas to drive every form change, so that contract drift is a compile error, not a runtime surprise.
16. As a QA engineer, I want Playwright `auth.setup` to sign in the seeded user (Sarah) directly against Cognito with her password and persist storage state, so that the authenticated e2e suite has a logged-in user without depending on an emailed code. (Amended — the original "via the real passwordless flow using the non-prod code tap" is dropped; the passwordless flow itself is covered at the unit/integration level since no real code is obtainable in tests.)

## Implementation Decisions

- **Registration wizard collapses** from three steps (About → Address → Credentials) to one (name + email); the address and credentials steps, the country selector usage there, and the password validation UI are deleted. The step-flow conventions (ADR-0016 family) still govern the two-screen shape: details → code.
- **One shared Verification screen** serves both flows (the backend decides what the code completes — never the UI). It owns: the one-time-code input, the generic error state, a resend affordance that re-submits the originating register/login request, and post-success navigation to the authenticated landing page. Entry state (which email, which flow for copy purposes only) carries via navigation state with the email as the recoverable fallback.
- **Hooks follow the existing mutation-hook pattern**: register and login hooks now navigate to the Verification screen; a new verify-code hook invalidates the user query on success (per ADR-0001, the user query remains the sole auth-state authority — Verification success manifests as that query turning authenticated).
- **Single-flight refresh middleware (deep module)** in the fetch layer: any 401 from a non-auth endpoint triggers exactly one refresh-token call (concurrent 401s coalesce onto the same in-flight promise), then one retry of the original request; refresh failure falls through to today's unauthenticated transition. The refresh request body is empty — the backend derives identity from cookies (backend ADR-0002). Auth endpoints are excluded to prevent loops.
- **Forms keep generated Zod schemas as the validation source** (ADR-0004); the regenerated client removes password/address/dateOfBirth from the register types, making every stale call site a compile error.
- **Copy uses the glossary**: "Confirmation code" in user-facing text (never "OTP"); "Sign up" / "Log in" in copy, Registration/Login/Verification in code. All password-related copy, links, and routes are removed.
- **Settings/profile surfaces** render email, date of birth, and address as immutable, with date of birth and address being Generated details (read-only display, no new edit affordances).
- **Playwright auth setup** signs in the seeded user (Sarah) by calling Cognito `InitiateAuth` with `USER_PASSWORD_AUTH` directly against the pool (username = Sarah's email, her seeded password, plus a `SECRET_HASH` computed from the app-client id/secret — the client has a secret), then injects the returned token triple into the webapp's auth cookies and persists storage state exactly as today. The client id/secret come from the same Cognito secret the backend reads; Sarah's password from the seeder config. No API login call, no tap polling. Single-user/serial constraints are unchanged. (Amended from "trigger login via the API, poll the infra code tap" — verify the exact `SECRET_HASH`/cookie-injection wiring during implementation.)

## Testing Decisions

- A good test asserts what a user or caller observes — rendered output, navigation, requests on the wire — never hook internals or component structure. Existing prior art: the Vitest + Testing Library unit/integration setup, the Playwright journey specs, and the dedicated axe-core a11y spec.
- **Refresh middleware** gets exhaustive Vitest coverage with a mocked fetch: 401 → refresh → retry success; concurrent 401s producing exactly one refresh; refresh failure → unauthenticated path; non-401s untouched; auth endpoints exempt; no retry storms.
- **Form/screen integration tests** (Vitest + Testing Library) for the collapsed registration form, email-only login form, and the shared Verification screen: validation from generated Zod schemas, generic error rendering, resend affordance behavior.
- **Playwright journeys** (amended — no emailed code is obtainable in tests, so flows needing a _real_ code can't run end-to-end against dev): the authenticated suite runs off Sarah's persisted storage state (signed-in landing, post-auth screens, signed-out redirect unchanged). Register/login/verify _flows_ (register → code → landing, login → code → landing, unconfirmed-rescue) move to unit/integration coverage with a mocked API rather than full e2e. Wrong-code generic error can still run e2e (a bogus code needs no real code). The register-skeleton and login specs are replaced to match the new flows; helper-links spec drops password links.
- **A11y spec** updated to sweep the new screens (registration single step, login email step, Verification screen).

## Out of Scope

- Backend behavior, contract authoring, and cookie semantics (backend PRD).
- Terraform and Lambda (infra PRD). The code-tap table is dropped entirely (infra ADR-0002); Playwright now authenticates Sarah directly against Cognito instead of consuming a tap.
- Proactive token-refresh timers (reactive single-flight is the whole design).
- Holder switching, KYC surfaces, profile editing beyond what exists.

## Further Notes

- The contract snapshot must be refreshed from the backend before regenerating the client; land this PRD's work only after the backend PRD's contract is on main.
- The visible payoff for demos: registration-to-accounts in under thirty seconds, with the branded code email on screen as part of the pitch.

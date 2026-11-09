# Finishing the auth surface: register, confirm, forgot, change, profile

Status: ready-for-agent

## Problem Statement

The auth tracer-bullet shipped a signed-in **User** the ability to log in, see their **Accounts**, drill into an **Account**'s transactions, and sign out. Every other auth-grade interaction a bank decision-maker expects to see in a demo — **Registration**, confirming the **Confirmation code** that lands in their inbox, recovering a forgotten password, changing the current password, and editing the parts of their **User** profile that aren't immutable identity — is missing. A demo that lets a prospect type their real name and watch a real bank-shaped app come into existence (and re-take ownership when they "forget" their password) is qualitatively more convincing than one that ships them a pre-baked Cognito identity.

This PRD finishes the Auth and User sections of the OpenAPI in one cohesive slice, so that the next demo can run the full lifecycle a real customer would: register from cold, confirm by code, sign in, edit their name, change their password, sign out, forget their password, reset it, sign in again. Every endpoint under those two OpenAPI sections (except the already-shipped login / signout / user-read / refresh-token) gets a UI home in this PRD.

## Solution

Six new routes, one repurposed top-right shell, and one new layout component compose the finished surface:

- **`/register/about` → `/register/address` → `/register/credentials`** — a three-step Registration form inside a `<RegisterLayout>` route that holds the shared `useForm<RegisterRequest>` instance via React context. A visible numbered progress indicator at the top of every step (`✔ 1 About you → ● 2 Address → 3 Credentials`) carries the user through. The final submit fires `POST /api/v1/auth/register` and navigates to `/confirm-register?email=<the just-typed email>`.
- **`/confirm-register?email=<encoded>`** — two-field form: email (pre-filled from the query, editable) + 6-digit **Confirmation code** rendered via `input-otp`. A Resend button calls `POST /api/v1/auth/resend-confirmation-code` with the email currently in the form; no cooldown. Submit fires `POST /api/v1/auth/confirm-register` and navigates to `/login?email=<encoded>` with a toast _"Account confirmed. You can now sign in."_
- **`/forgot-password`** — single-field form: email. Submit fires `POST /api/v1/auth/forgot-password` and navigates to `/confirm-forgot-password?email=<encoded>` with a toast _"Reset code sent to <email>."_
- **`/confirm-forgot-password?email=<encoded>`** — three fields on one page: email (pre-filled, editable), **Confirmation code** (`input-otp`), new password. A Resend button **re-fires** `POST /api/v1/auth/forgot-password` (not `resend-confirmation-code` — that endpoint is for registration confirmation only). Submit fires `POST /api/v1/auth/confirm-forgot-password` and navigates to `/login?email=<encoded>` with a toast _"Password reset. You can now sign in."_
- **`/settings`** — single signed-in page containing two cards: **Profile** (renders all four identity fields; firstName and lastName editable via `PUT /api/v1/user`; email and dateOfBirth rendered read-only with the helper "Contact support to change") and **Security** (current password + new password, submit fires `POST /api/v1/auth/change-password`). Each card has its own form, its own submit button, and its own success toast (_"Profile updated."_ / _"Password updated."_). Success clears the password fields and keeps the user on the page.
- **`<LoginPage>`** gains three helper links below the form: "Forgot your password?" → `/forgot-password`; "Don't have an account? Sign up" → `/register/about`; "Already registered but haven't confirmed? Confirm your account" → `/confirm-register`. The login form's email field also accepts a `?email=` query pre-fill so post-confirm and post-reset land cleanly.
- **`<AppShell>`** top-right is converted from `<displayName /> [Sign out]` into a `DropdownMenu` (radix-ui) trigger that opens a small menu: _Settings_ (navigates to `/settings`) and _Sign out_ (existing behaviour). PrimaryNav (currently `Accounts`) is unchanged.
- **`<Unauthenticated>`** — a new layout-route component that mirrors `<Authenticated>`: while `useUser()` is `'unknown'` it renders a skeleton; when `'authenticated'` it renders `<Navigate to="/" replace />`; when `'unauthenticated'` it renders `<Outlet />`. All public auth routes (`/login`, `/register/*`, `/confirm-register`, `/forgot-password`, `/confirm-forgot-password`) sit under this layout. This generalises the manual `useEffect → navigate` dance currently in `LoginPage`.

The slice exercises every load-bearing decision from the auth tracer-bullet's grilling session against five new mutations, three new query-cache interactions, six new route components, and one chrome change. After this PRD lands, the **Auth** and **User** sections of the OpenAPI are fully UI-covered except for the deliberately-unshipped pieces (refresh-token is automated by the cookie + global-401 mechanism; account-holder profile and address flows are out of scope per ADR boundaries).

## User Stories

1. As a prospect on `/login`, I want a "Sign up" link that takes me to a real registration form, so that I can see the bank's onboarding without having to be handed a demo account.
2. As a new **User** on `/register/about`, I want to enter my first name, last name, and date of birth, see a visible step indicator showing me I'm on step 1 of 3, and proceed when I press Continue, so that I know how much work the registration will take.
3. As a new **User** on `/register/address`, I want to enter line 1, optional line 2, city, postcode, and country — with country locked to "United Kingdom" because this is a UK bank — and step back to step 1 if I need to fix something, without losing what I typed there.
4. As a new **User** on `/register/credentials`, I want my chosen password to be validated against the bank's policy _before_ I press Create account, so that I am not told post-submit that my password is unacceptable.
5. As a new **User** who has just pressed Create account, I want to be taken to `/confirm-register` with my email pre-filled, so that I do not have to re-type it.
6. As a new **User** on `/confirm-register`, I want a clean 6-digit code-entry field that accepts paste and auto-advances between slots, plus a Resend button that re-fires the email, so that I am not blocked by a code that didn't arrive.
7. As a **User** who closed the tab between register and confirm, I want a "Confirm your account" link on `/login` that takes me back to `/confirm-register`, so that I can finish the flow without re-registering.
8. As a **User** who has just confirmed my account, I want to be taken to `/login` with my email pre-filled and a "Account confirmed" toast, so that I know the flow succeeded and the next step is to sign in.
9. As a signed-out **User** who has forgotten my password, I want a "Forgot password?" link on `/login` that takes me to a single-field reset form, so that I can recover access in two screens.
10. As a **User** on `/confirm-forgot-password`, I want to enter the code, type a new password, and complete the reset in one screen, so that the flow is no longer than register's confirm step.
11. As a signed-in **User**, I want a dropdown menu in the top-right that holds Settings and Sign out, so that the chrome looks like a real bank and not a one-off Sign-out button.
12. As a signed-in **User** on `/settings`, I want to see my full identity (firstName, lastName, email, dateOfBirth) rendered, even though only firstName and lastName are editable, so that I know what the bank holds on me.
13. As a signed-in **User** editing my profile, I want a clear "Contact support to change" note next to email and dateOfBirth, so that I am not surprised those fields are read-only.
14. As a signed-in **User** changing my password, I want to type my current password and a new one, and have my session continue uninterrupted after success, so that the demo does not get disrupted mid-flow.
15. As a signed-in **User**, I want my display name in the top-right to update immediately after I edit it, so that I have confidence the save worked.
16. As a signed-in **User**, I want to be redirected back to `/` if I navigate to `/register` or `/forgot-password`, so that the signed-in app never accidentally shows me a signed-out flow.
17. As a **User** who typed the wrong current password on the change-password form, I want an error indication that I'm still signed in (not bounced to `/login`), so that I can re-try.
18. As a **User** whose register submit failed because the email is already registered, I want a generic error that does not confirm whether the email actually exists in the system, so that the system does not leak my membership to a malicious enumerator.
19. As a **User** whose confirmation code entry failed for any reason — wrong code, expired code, already confirmed — I want a generic error that does not distinguish the failure mode, so that an attacker brute-forcing codes against a known email gets no oracle.
20. As a developer adding a new public auth route later, I want to drop it under `<Unauthenticated>` and have the gate apply automatically, so that I cannot forget to redirect signed-in users.
21. As a developer regenerating the API client after the Cognito password policy gains a `pattern` and `minLength`, I want every password-bearing form to fail to type-check until I extend the form schema, so that drift between Cognito and the client is impossible at compile time.
22. As a developer running E2E tests, I want the existing shared-Cognito-user pattern to be preserved and the new email-dependent flows (register / confirm / forgot / change) to be Vitest-only, so that the E2E suite does not depend on inbox reading or on creating real Cognito users on every CI run.
23. As a developer reading the codebase, I want the global 401 handler from ADR-0001 to remain free of per-mutation opt-outs, with backend disambiguation handling the change-password wrong-previous-password case via a 400, so that the sign-out machinery stays honest.
24. As a developer reading the codebase, I want all auth-surface error copy to be either the existing wrong-password-on-login carve-out or a generic toast, with anti-enumeration as the documented reason (ADR-0007), so that the next contributor cannot innocently add a "Wrong code" inline message.
25. As a **User** using a screen reader through the multi-step register flow, I want focus to land on each step's `<h1>` after the route changes, and the step indicator to be announced as a progress region, so that I always know which step I am on without scanning the page.
26. As a **User** with `prefers-reduced-motion` enabled, I want the step transitions and the dropdown menu open/close to respect that preference, so that none of the new chrome introduces motion my OS asked the app to suppress.

## Implementation Decisions

This PRD enacts the seven ADRs on the branch (`docs/adr/0001` through `0007`, the last of which is introduced by this PRD) and the additional grilling decisions that live in the conversation around them. The PRD does not re-litigate any of these; it commits to them as the contract for implementation.

### Backend dependencies — must land before webapp work

Two changes are required in `malai-backend`'s hand-authored OpenAPI and the corresponding controllers, **before** the webapp work in this PRD can complete. They are listed here so the dependency is visible at PRD-time, not discovery-time.

1. **Cognito password policy in the OpenAPI.** The Cognito user-pool policy lives in `malai-infra` IaC. Read the current policy, then encode it as `pattern` + `minLength` on three OpenAPI schema fields: `RegisterRequest.password`, `ConfirmForgotPasswordRequest.password`, `ChangePasswordRequest.proposedPassword`. Add a comment beside each field naming the matching `malai-infra` resource so future Cognito-policy edits cannot silently drift. With this change, `npm run openapi:gen` re-emits Zod schemas that enforce the policy client-side; React Hook Form + `zodResolver` pipelines for the three forms get the validation for free per ADR-0004.
2. **Change-password contract split.** `POST /api/v1/auth/change-password` currently documents `401` as its only failure response. Split it: return `400` (`ErrorResponse` shape, `MalaiCode` of the backend's choosing) when the _previous password_ is wrong; reserve `401` for _not authenticated_ (truly stale token / revoked session). Update the OpenAPI's response list to `200 / 400 / 401`. The reason matters: without this split, the global 401 handler from ADR-0001 signs the user out on a wrong-previous-password keystroke. See ADR-0007 for the broader policy this slots into.

The webapp side of both changes — refreshing this repo's `./openapi.yaml` snapshot and running `npm run openapi:gen` — is the standard contract-refresh workflow per `malai-webapp/CLAUDE.md`.

### Architectural shape

- **`useRegister()`** is a deep hook. Surface: `{ register(input), isPending }`. Internally wraps the generated register mutation; `onSuccess` navigates to `/confirm-register?email=<input.email>`. No optimistic user-query writes — the just-registered User is **Unconfirmed** and cannot yet appear authenticated.
- **`useConfirmRegister()`** is a deep hook. Surface: `{ confirmRegister(input), isPending }`. Internally wraps the generated mutation; `onSuccess` navigates to `/login?email=<input.email>` and fires the _"Account confirmed."_ toast.
- **`useResendConfirmationCode()`** is a deep hook. Surface: `{ resend(email), isPending }`. Wraps the generated mutation; `onSuccess` fires a small _"Code re-sent."_ toast.
- **`useForgotPassword()`** is a deep hook. Surface: `{ forgotPassword(email), isPending }`. `onSuccess` navigates to `/confirm-forgot-password?email=<email>` and fires _"Reset code sent."_ The same hook backs the Resend affordance on `/confirm-forgot-password`.
- **`useConfirmForgotPassword()`** is a deep hook. Surface: `{ confirmForgotPassword(input), isPending }`. `onSuccess` navigates to `/login?email=<input.username>` and fires _"Password reset."_
- **`useChangePassword()`** is a deep hook. Surface: `{ changePassword(input), isPending }`. `onSuccess` fires _"Password updated."_ and resets the form's password fields via the caller-supplied `form.reset(...)`. `onError` for 400 is allowed to fall through to the generic toast (per ADR-0007 — no inline disclosure of wrong previous password); `onError` for 401 is allowed to fall through to the global handler (correctly signs the user out, per ADR-0001 — now genuinely meaning the session is dead, because 400 took the other path).
- **`useUpdateUser()`** is a deep hook. Surface: `{ updateUser(input), isPending }`. `onSuccess` writes the response into the user-query cache via `queryClient.setQueryData(getApiV1UserQueryKey(), response)` so the **AppShell** display name updates without a re-fetch round-trip. Fires _"Profile updated."_

### Composition layer

- **`<Unauthenticated>`** is a layout route component. It reads `useUser()` and:
  - renders the same full-page skeleton as `<Authenticated>` while `status === 'unknown'`,
  - renders `<Navigate to="/" replace />` when `status === 'authenticated'`,
  - renders `<Outlet />` when `status === 'unauthenticated'`.
- **`<RegisterLayout>`** is a nested layout route at `/register`. It owns a single `useForm<RegisterRequest>({ resolver: zodResolver(zRegisterRequest), shouldFocusError: true })` instance, exposes it via `RegisterFormContext.Provider`, and renders the **step indicator** above an `<Outlet />`. Sub-routes (`/register/about`, `/register/address`, `/register/credentials`) consume the form via `useRegisterFormContext()` and call `form.register('firstName')` etc. directly. Per-step validation is performed by `.pick()` on `zRegisterRequest` (or by trigger-only-the-current-step's-fields via `form.trigger(['firstName', 'lastName', 'dateOfBirth'])`). On step 1's Continue, validate fields-in-step-1; on step 2's Continue, validate fields-in-steps-1-and-2; on step 3's Create account, validate the whole form and fire the mutation. Steps 2 and 3 guard on prior-step data via `<Navigate to="/register/about" replace />` when any required prior field is empty; this is the refresh-restart contract.
- **Step indicator** (`<RegisterStepIndicator step={1 | 2 | 3} />`) renders three labelled, numbered chips connected by arrows: `[✔ 1 About you] → [● 2 Address] → [   3 Credentials]`. Marks completed steps with a check, the current step filled, and future steps plain. Implementation uses Tailwind tokens (no literal palette colors per the ESLint rule from the auth tracer-bullet) and respects `motion-safe` for any transition. The indicator's container has `role="progressbar"` with `aria-valuenow`, `aria-valuemin={1}`, `aria-valuemax={3}` and an `aria-valuetext` like `"Step 2 of 3: Address"`.
- **`<ConfirmCodeField>`** is a small wrapper around `input-otp` configured for 6 slots, numeric-only, with `autoComplete="one-time-code"`. Shared between `/confirm-register` and `/confirm-forgot-password`. Exposes RHF-compatible props; pairs with a `<Label>` and an inline error region.
- **`<CountrySelect>`** is a disabled shadcn `<Select>` whose only option is `GB` ("United Kingdom"). Renders inside `/register/address` as the country field. The schema accepts any 2-letter ISO code, but the UI locks to GB; if a future demo expands the catalogue, the change is one option in this component.
- **`<ConfirmRegisterPage>`**, **`<ForgotPasswordPage>`**, **`<ConfirmForgotPasswordPage>`** all follow the LoginPage shape: focusable `<h1>` via `useFocusOnMount`, RHF + `zodResolver` (using `.pick()` from `zConfirmRegisterRequest`, `zForgotPasswordRequest`, `zConfirmForgotPasswordRequest` respectively, or the schema directly when shape matches), `aria-invalid` / `aria-describedby` on each field, and a generic-error fall-through.
- **`<SettingsPage>`** sits under `<Authenticated>`. Two `<Card>`s stacked vertically on a max-width container, each with its own `<form>`. The **Profile** card uses `useForm({ defaultValues: user.firstName/lastName, resolver: zodResolver(zUpdateUserRequest) })`; the read-only email and dateOfBirth render as `<dl>` rows beneath the editable fields with helper text. The **Security** card uses `useForm({ resolver: zodResolver(zChangePasswordRequest) })`; on success calls `form.reset()` to clear the inputs. Both submit buttons are disabled while their respective mutations are pending. Each card's `<h2>` is focusable and announced; the `<h1>` of the page is "Account settings".
- **`<AppShell>` top-right**: replace the inline `<displayName /> [Sign out]` with a `<DropdownMenu>` (radix-ui — already in deps, not shadcn's wrapper, to match the existing `radix-ui` usage in `PrimaryNav`'s `Collapsible`). The trigger is `<button>{displayName} <ChevronDown /></button>`. The menu items are `<DropdownMenu.Item asChild><Link to="/settings">Settings</Link></DropdownMenu.Item>` and a "Sign out" item that fires `useSignout().signout()`. The dropdown's open/close transition respects `motion-safe`. Keyboard navigation (arrow keys, escape, enter) is provided by radix's primitive.
- **Router** declares two route trees. Under `<Unauthenticated>`: `/login`, `/register` (layout) with children `/about`, `/address`, `/credentials`, `/confirm-register`, `/forgot-password`, `/confirm-forgot-password`. Under `<Authenticated>`: `/` (Accounts), `/accounts/:accountUid` (existing), `/settings` (new). The router is still configured **without** loaders per ADR-0002.
- **Toasts**: every success-path uses the existing Sonner instance (the same one wired up at the auth tracer-bullet). The default Sonner position and styling are unchanged. Generic error toasts continue to be fired by the QueryClient's global mutation error handler.
- **Lint**: no new lint rule is required. The existing Tailwind class-restriction rule from the auth tracer-bullet covers every new page.

### Error handling, per ADR-0007

Every new mutation in this PRD relies on the global mutation `onError` handler (set up at the auth tracer-bullet) to surface a generic toast. **No mutation in this PRD installs a per-call `setError('root', ...)` inline message.** The only carve-out in the codebase remains `useLogin`'s wrong-password handling, which is itself anti-enumeration-aligned (same copy for unknown email vs wrong password). When the next contributor proposes an inline message in any auth flow, they need an ADR explaining why the disclosure is safe.

Specifically:

- Register `UsernameExistsException` → generic toast. The form re-enables.
- Confirm-register `CodeMismatchException` / `ExpiredCodeException` / `NotAuthorizedException` → generic toast.
- Forgot-password 4xx → generic toast.
- Confirm-forgot-password 4xx → generic toast.
- Change-password 400 (wrong previous password, post-contract-split) → generic toast. User remains signed in.
- Change-password 401 (not authenticated, post-contract-split) → falls through to the global 401 handler from ADR-0001, which invalidates the user query and bounces to `/login`. Correct behaviour.
- PUT /user 400 → generic toast.

Client-side validation via the generated Zod schemas covers empty fields, malformed email, weak passwords (post-OpenAPI-policy-update), invalid 2-letter country codes. Those messages describe the user's _own_ input and carry no enumeration signal.

### Backend contract dependencies (changes already enumerated above)

- `POST /api/v1/auth/register` — used by `useRegister`.
- `POST /api/v1/auth/confirm-register` — used by `useConfirmRegister`.
- `POST /api/v1/auth/resend-confirmation-code` — used by `useResendConfirmationCode` (registration confirmation's Resend only).
- `POST /api/v1/auth/forgot-password` — used by `useForgotPassword` (also re-called as the Resend mechanism on `/confirm-forgot-password`).
- `POST /api/v1/auth/confirm-forgot-password` — used by `useConfirmForgotPassword`.
- `POST /api/v1/auth/change-password` — used by `useChangePassword`. **Requires the 400-vs-401 contract split above.**
- `PUT /api/v1/user` — used by `useUpdateUser`.

If any of these contracts shift while implementing, refresh `./openapi.yaml`, run `npm run openapi:gen`, and chase the TypeScript errors per the webapp `CLAUDE.md` workflow.

## Testing Decisions

Tests in this PRD inherit every rule from the auth tracer-bullet: assert externally observable behaviour; reuse generated types and Zod schemas to construct fixtures so contract drift breaks compilation; no mocks in E2E; no comments in test files. The new wrinkle is the **email-dependent E2E gap**.

### E2E coverage cut (constrained by the shared-user pattern)

The repo's E2E pattern uses a single fixed Cognito user fetched from AWS Secrets Manager (`mal-dev-malai-api-end-to-end-test-user`), with `workers=1, fullyParallel=false`, and reused via `tests/e2e/auth.setup.ts`'s `storageState`. This is preserved.

Flows that require **a real email round-trip or that would mutate the shared user's auth state** are **deliberately not E2E-tested in this PRD** — Playwright cannot read inboxes and the shared user cannot re-register or have their password changed without breaking every other E2E test in the suite. Specifically:

- **Register / confirm-register / resend-confirmation-code** — Vitest-only.
- **Forgot-password / confirm-forgot-password** — Vitest-only.
- **Change-password** — Vitest-only.

When a future PRD introduces a backend test-only mechanism for fetching codes (or programmatically confirming users), the E2E coverage can be back-filled. That mechanism is out of scope here.

What **does** get E2E coverage:

- **`<Unauthenticated>` redirect** — using the shared signed-in `storageState`: visit `/register/about`, assert redirect to `/`. Visit `/forgot-password`, assert redirect to `/`.
- **Login auxiliary links navigate correctly** — without `storageState`: visit `/login`, click each of the three helper links, assert correct landing route.
- **AppShell user dropdown** — using the shared signed-in `storageState`: visit `/`, open the dropdown, assert "Settings" and "Sign out" menu items are present and reachable by keyboard.
- **`/settings` renders the full identity** — using the shared signed-in `storageState`: visit `/settings`, assert all four identity fields render with the editable ones as inputs and email + DoB as read-only text with the "Contact support" affordance.
- **PUT /user happy path (idempotent)** — using the shared signed-in `storageState`: visit `/settings`, change firstName, save, assert the AppShell display name updates, then change it back to the original value. This is the one new mutation that's safe to E2E because it's idempotent and reversible on the shared user.

### Unit tests (Vitest)

Every deep hook gets a spec. Patterns follow `useLogin.test.tsx`:

- **`useRegister`** — happy path navigates to `/confirm-register?email=...`; 4xx falls through to the global toast (assert no inline setError).
- **`useConfirmRegister`** — happy path navigates to `/login?email=...` and fires the success toast; 4xx falls through to the toast.
- **`useResendConfirmationCode`** — happy path fires the _"Code re-sent."_ toast; 4xx falls through.
- **`useForgotPassword`** — happy path navigates to `/confirm-forgot-password?email=...`; 4xx falls through.
- **`useConfirmForgotPassword`** — happy path navigates to `/login?email=...`; 4xx falls through.
- **`useChangePassword`** — happy path fires _"Password updated."_ and clears the password fields (assert `form.reset` was called); 400 falls through to the toast and **does not** invalidate the user query; 401 falls through to the global handler (assert the user query is invalidated — covers the contract-split semantics).
- **`useUpdateUser`** — happy path writes the response to the user-query cache via `setQueryData` (assert the cache content directly) and fires the toast; 4xx falls through.

Plus a small spec for `<RegisterLayout>` covering: step 2 redirects to step 1 when prior-step data is missing; the form context is correctly threaded; per-step `trigger()` validates only that step's fields. And a `<RegisterStepIndicator>` spec for the `aria-valuetext` / `role="progressbar"` semantics.

### Accessibility (`tests/e2e/a11y.spec.ts`)

Add every new route to the existing axe spec. Public routes (`/register/about`, `/register/address`, `/register/credentials`, `/confirm-register`, `/forgot-password`, `/confirm-forgot-password`) and the new authenticated route (`/settings`) must each pass with zero violations on the default `malai` theme in both light and dark modes. The step indicator's `role="progressbar"` is the new pattern axe-core should see.

Focus management per ADR-0006 extends to every new page: `<h1>` is focusable on mount via `useFocusOnMount`. For `<RegisterLayout>`, focus moves to the new step's `<h1>` after a Continue / Back navigation — the layout component drives it because the sub-routes' `<h1>` is theirs.

## Out of Scope

- **Backend test mechanism for E2E coverage of email-dependent flows.** Without it, register / confirm / forgot / change-password are Vitest-only in this PRD.
- **A "Sign out from all devices" gesture on change-password.** Real-bank feature; not in OpenAPI; backend coordination required; cross-cutting with refresh-token semantics. Defer.
- **Email change as a self-service flow.** The OpenAPI's `UpdateUserRequest` excludes email; a real change requires re-verification. Out of scope; the UI surfaces this as the "Contact support to change" read-only treatment.
- **DateOfBirth change as a self-service flow.** Same reasoning; KYC paperwork in a real bank.
- **Country expansion beyond GB on `/register/address`.** UK-only is the deliberate v1 demo posture.
- **Password strength meter on `/register/credentials` and `/settings/security`.** The OpenAPI policy regex gives binary valid / invalid feedback; helper text under the password field listing the policy requirements is sufficient. A meter is a polish add for a later PRD if a demo audience surfaces it.
- **Persisting register multi-step state across refresh.** Refresh restarts the flow; password never touches `sessionStorage`. Reconsider if a demo narrative surfaces the refresh-mid-register pain.
- **Auto-redirect to `/confirm-register` on UserNotConfirmedException at login.** Smaller surface than the three-link approach; ADR-0007 (anti-enumeration) discourages distinguishing this failure mode anyway.
- **Account-holder profile and address edits** (the `/api/v1/account-holder/*` PUT-style endpoints). The PRD covers Auth + User OpenAPI sections only.
- **Refresh-token UI.** Already automated by the cookie + global-401 mechanism per ADR-0001.
- **Translation of `MalaiCode` to copy.** Per ADR-0007.
- **Workspace tooling generating the `MalaiCode → copy` table.** Per ADR-0007.
- **i18n / translation.** UI copy is English-only for v1.
- **Sub-routes for `/settings` (`/settings/profile`, `/settings/security`).** Promote when the surface grows beyond two cards.
- **A holder switcher in the AppShell.** Still deferred until a demo scenario surfaces a second holder type, per the auth tracer-bullet PRD.

## Further Notes

- The webapp `CLAUDE.md` elevates `/tdd` for this repo specifically. The natural starting move is the unit spec for `useRegister`, driving the hook into existence, then the `<RegisterLayout>` and `<RegisterStepIndicator>` skeletons, then each step's page. Forgot-password and change-password reuse every pattern Registration discovers.
- The new copy in this PRD ("Account settings", "About you", "Address", "Credentials", "Continue", "Back", "Create account", "Confirm your email", "Resend code", "Code re-sent.", "Account confirmed. You can now sign in.", "Reset your password", "Choose a new password", "Reset code sent to <email>.", "Password reset. You can now sign in.", "Profile updated.", "Password updated.", "Contact support to change", "Don't have an account? Sign up", "Already registered but haven't confirmed? Confirm your account", "Forgot your password?", "Something went wrong. Please try again.") is the v1 contract. Subsequent PRDs may revisit but should not silently diverge.
- The radix-ui `DropdownMenu` import sits alongside the existing `Collapsible` import in `AppShell.tsx`. Both come from the `radix-ui` umbrella package already in deps. Resist the urge to switch to `@radix-ui/react-dropdown-menu` or to introduce a new shadcn wrapper — match the existing style.
- The Cognito password policy synchronisation flagged in ADR-0004 is resolved by the backend dependency above. When Cognito policy changes in future, the OpenAPI must be updated in lock-step; a comment on each affected schema field names the matching `malai-infra` resource so the dependency is grep-able.
- The change-password 400-vs-401 contract split is the load-bearing reason ADR-0007 exists in this PRD's branch. Without the split, the policy of "generic toast for wrong previous password" cannot be enforced without also kicking the user out of the session.
- After this PRD lands, the Auth and User sections of the OpenAPI are fully UI-covered. The next domain to grill is **AccountHolder** (joint / business / sole-trader, holder switcher, address and profile edits) — distinct enough from this surface that it warrants its own PRD.

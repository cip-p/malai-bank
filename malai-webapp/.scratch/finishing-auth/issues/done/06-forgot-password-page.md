# `/forgot-password` page + `useForgotPassword` + navigation to `/confirm-forgot-password?email=`

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

The first step of the **password reset** flow. A single-field form that kicks off Cognito's forgot-password email and navigates the user to the confirmation step.

**`useForgotPassword()`** at `src/auth/useForgotPassword.ts` — deep hook. Surface: `{ forgotPassword(email), isPending }`. Wraps `postApiV1AuthForgotPasswordMutation()`. On success, navigates to `/confirm-forgot-password?email=<encoded email>` and fires the Sonner toast _"Reset code sent to <email>."_ On 4xx, the error falls through to the global generic toast (per ADR-0007 — backends should be configured to return 200 for unknown emails per Cognito's anti-enumeration convention; a 4xx is treated as opaque).

The same hook is **re-used as the Resend mechanism** on `/confirm-forgot-password` (slice 07). Built once here.

**`<ForgotPasswordPage>`** at `src/pages/ForgotPasswordPage.tsx`:

- `<h1>` "Reset your password" focused on mount via `useFocusOnMount`.
- Single-field `<form>`: email.
- `useForm({ resolver: zodResolver(zForgotPasswordRequest), shouldFocusError: true })`.

  Note: `ForgotPasswordRequest` in the OpenAPI uses `username`, not `email`. The form field can be labelled "Email" in copy while submitting the value as `username` to the mutation. Demo-grade UK retail bank UX (Cognito's "username" is the email in practice).

- Submit button "Send reset code" (shows "Sending…" while pending).
- Helper text under the field: _"Enter your email and we'll send you a 6-digit code to reset your password."_

Route declared under the existing `<Unauthenticated>` parent from issue 02. Per ADR-0007 no inline `setError('root', ...)` calls — every 4xx is a generic toast.

## Acceptance criteria

- [ ] `src/auth/useForgotPassword.ts` exports `useForgotPassword()` with `{ forgotPassword(email), isPending }`.
- [ ] On success, `useForgotPassword().forgotPassword(email)` navigates to `/confirm-forgot-password?email=<URL-encoded email>` and fires the _"Reset code sent to <email>."_ toast (the actual email is interpolated into the toast text).
- [ ] On 4xx, the hook does not install a per-call `setError`; falls through to the global generic toast.
- [ ] `<ForgotPasswordPage>` at `/forgot-password` (under `<Unauthenticated>`) renders the focused `<h1>` "Reset your password", a single email field, and a "Send reset code" submit button.
- [ ] The email field is labelled "Email" in copy but the request body maps to the OpenAPI's `username` field.
- [ ] Submit button shows "Sending…" while the mutation is pending; disabled in that state.
- [ ] Unit tests for `useForgotPassword`: happy path navigates + fires success toast; 4xx falls through.
- [ ] Vitest UI test for `<ForgotPasswordPage>`: client-side validation catches empty / malformed email; submitting valid email fires the mutation.
- [ ] No E2E for this slice — email-dependent flow, Vitest-only per the PRD's testing decisions.
- [ ] `/forgot-password` added to the existing axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [02-unauthenticated-layout-and-login-email-prefill.md](./02-unauthenticated-layout-and-login-email-prefill.md)

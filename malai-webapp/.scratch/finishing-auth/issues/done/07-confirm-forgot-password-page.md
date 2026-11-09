# `/confirm-forgot-password`: 3 fields + `useConfirmForgotPassword` + Resend re-fires `useForgotPassword`

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

The second step of the **password reset** flow. One screen, three fields (email, confirmation code, new password), one mutation. On success the user lands on `/login?email=<encoded>` with a _"Password reset. You can now sign in."_ toast.

**`useConfirmForgotPassword()`** at `src/auth/useConfirmForgotPassword.ts` — deep hook. Surface: `{ confirmForgotPassword(input), isPending }`. Wraps `postApiV1AuthConfirmForgotPasswordMutation()`. On success navigates to `/login?email=<encoded input.username>` and fires the Sonner toast _"Password reset. You can now sign in."_ On 4xx falls through to the global generic toast (no inline disclosure per ADR-0007).

**`<ConfirmForgotPasswordPage>`** at `src/pages/ConfirmForgotPasswordPage.tsx`:

- `<h1>` "Choose a new password" focused on mount via `useFocusOnMount`.
- Reads `?email=` and uses it as the email field's `defaultValue` (empty if absent).
- Three fields under one `<form>`:
  1. email (`type="email"`, editable)
  2. `<ConfirmCodeField name="confirmationCode" />` (the same component from slice 05)
  3. password (`type="password"`, with the policy helper text — same copy as `<RegisterCredentialsPage>`)
- `useForm({ resolver: zodResolver(zConfirmForgotPasswordRequest), shouldFocusError: true })`. Because OpenAPI was refreshed in slice 01, `zConfirmForgotPasswordRequest.password` now carries the Cognito `pattern` + `minLength`; weak passwords surface inline.
- Submit button "Reset password" (shows "Resetting…" while pending).
- A Resend button beside the submit, **calling `useForgotPassword().forgotPassword(form.getValues('email'))` from slice 06** (re-fires the forgot-password email — `resend-confirmation-code` is for registration confirmation only, not this flow). Disabled while either mutation is pending; no cooldown.
- Helper text below the code field: _"We sent a 6-digit code to {email}. Didn't get it? Resend it."_

Route declared under `<Unauthenticated>`. Per ADR-0007 no inline `setError('root', ...)` calls.

## Acceptance criteria

- [ ] `src/auth/useConfirmForgotPassword.ts` exports `useConfirmForgotPassword()` with `{ confirmForgotPassword(input), isPending }`.
- [ ] Happy path navigates to `/login?email=<URL-encoded input.username>` and fires the _"Password reset. You can now sign in."_ toast.
- [ ] On 4xx, falls through to the global generic toast; no inline disclosure.
- [ ] `<ConfirmForgotPasswordPage>` at `/confirm-forgot-password` (under `<Unauthenticated>`) renders the focused `<h1>`, the three-field form, a Reset password submit button, and a Resend button.
- [ ] Email field defaults from `?email=` and is editable.
- [ ] `<ConfirmCodeField>` from slice 05 is reused.
- [ ] Password field renders the policy helper text (matching `<RegisterCredentialsPage>`); client-side validation surfaces inline errors when the password violates `zConfirmForgotPasswordRequest`.
- [ ] Resend button calls `useForgotPassword().forgotPassword(form.getValues('email'))` (re-fires the forgot-password email).
- [ ] Both buttons disabled while either mutation is pending; otherwise the Resend has no cooldown.
- [ ] No inline `setError('root', ...)` calls anywhere in this slice.
- [ ] Unit tests for `useConfirmForgotPassword`: happy path navigates + fires success toast; 4xx falls through.
- [ ] Vitest UI test for `<ConfirmForgotPasswordPage>`: email pre-fills from `?email=`; Resend uses current field value (not original query); password validation surfaces policy errors inline.
- [ ] No E2E for this slice — email-dependent flow, Vitest-only per the PRD's testing decisions.
- [ ] `/confirm-forgot-password?email=jane%40example.com` added to the existing axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [01-backend-prereq-openapi-refresh.md](./01-backend-prereq-openapi-refresh.md)
- [06-forgot-password-page.md](./06-forgot-password-page.md)

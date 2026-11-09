# `/confirm-register`: `<ConfirmCodeField>` + `useConfirmRegister` + `useResendConfirmationCode` + post-confirm navigation/toast

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Replace the slice-04 stub `<ConfirmRegisterPage>` with the real two-field form, plus the Resend affordance and the post-confirm navigation contract.

**`<ConfirmCodeField>`** at `src/components/ConfirmCodeField.tsx` — a small wrapper around `input-otp` (already in deps) configured for 6 slots, numeric-only (`inputMode="numeric"` and digit-only `pattern`), with `autoComplete="one-time-code"`. RHF-compatible: accepts `name`, integrates with `form.register(...)` or `<Controller>` whichever is cleaner against `input-otp`'s API. Pairs with a `<Label>` and an inline error region. Shared by `/confirm-register` (this slice) and `/confirm-forgot-password` (slice 07).

**`useConfirmRegister()`** at `src/auth/useConfirmRegister.ts` — deep hook. Surface: `{ confirmRegister(input), isPending }`. Wraps `postApiV1AuthConfirmRegisterMutation()`. On success, navigates to `/login?email=<encoded input.email>` and fires a Sonner toast: _"Account confirmed. You can now sign in."_ On 4xx (CodeMismatchException, ExpiredCodeException, NotAuthorizedException), the error falls through to the global toast handler — generic _"Something went wrong. Please try again."_ No inline disclosure of which failure mode occurred (per ADR-0007, denying enumeration signal on confirmation-code attempts).

**`useResendConfirmationCode()`** at `src/auth/useResendConfirmationCode.ts` — deep hook. Surface: `{ resend(username), isPending }`. Wraps `postApiV1AuthResendConfirmationCodeMutation()`. On success fires a small Sonner toast: _"Code re-sent."_ On 4xx falls through to generic toast. No cooldown — the Resend button is always enabled (Cognito enforces rate limiting server-side).

**`<ConfirmRegisterPage>`** at `src/pages/ConfirmRegisterPage.tsx`:

- `<h1>` "Confirm your email" focused on mount via `useFocusOnMount`.
- Reads `?email=` and uses it as the email field's `defaultValue` (empty if absent — the closed-tab re-entry case).
- Two fields under one `<form>`: email (`type="email"`, editable) and `<ConfirmCodeField name="confirmationCode" />`.
- Form uses `useForm({ resolver: zodResolver(zConfirmRegisterRequest), shouldFocusError: true })`.
- A Confirm submit button (shows "Confirming…" while pending).
- A Resend button (type="button") beside the submit, calling `useResendConfirmationCode().resend(form.getValues('email'))` — disabled while either mutation is pending.
- Helper text below the code field: _"We sent a 6-digit code to {email}. Didn't get it? Resend it."_

Per ADR-0007 there are no inline `setError('root', ...)` calls in this slice — every 4xx is a generic toast.

## Acceptance criteria

- [ ] `src/components/ConfirmCodeField.tsx` exists; renders 6-slot `input-otp` with numeric input mode, `autoComplete="one-time-code"`, RHF-compatible props. Visible inline error region.
- [ ] `src/auth/useConfirmRegister.ts` exports `useConfirmRegister()` with `{ confirmRegister(input), isPending }`; success navigates to `/login?email=<URL-encoded input.email>` and fires the _"Account confirmed. You can now sign in."_ toast.
- [ ] `src/auth/useResendConfirmationCode.ts` exports `useResendConfirmationCode()` with `{ resend(username), isPending }`; success fires the _"Code re-sent."_ toast.
- [ ] `<ConfirmRegisterPage>` replaces the stub from slice 04; renders the focused `<h1>`, the email + code form, and a Resend button.
- [ ] Email field defaults from `?email=` query; editable.
- [ ] Confirm submit button disabled while either mutation is pending and shows "Confirming…".
- [ ] Resend button disabled while either mutation is pending; otherwise always enabled (no cooldown).
- [ ] No inline `setError('root', ...)` calls anywhere in this slice's source — every 4xx falls through to the global toast handler (anti-enumeration per ADR-0007).
- [ ] Unit tests for `useConfirmRegister`: happy path navigates + fires success toast; 4xx falls through (no setError, no user-query invalidation).
- [ ] Unit tests for `useResendConfirmationCode`: happy path fires success toast; 4xx falls through.
- [ ] Vitest UI test for `<ConfirmRegisterPage>`: email pre-fills from `?email=`; Resend uses the current email field value (not the original query); Confirm submits with both fields.
- [ ] No E2E for this slice — email-dependent flow, Vitest-only per the PRD's testing decisions.
- [ ] `/confirm-register?email=jane%40example.com` is added to the existing axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [02-unauthenticated-layout-and-login-email-prefill.md](./02-unauthenticated-layout-and-login-email-prefill.md)

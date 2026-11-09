# `/settings` Security card: `useChangePassword` + 400 generic toast + 401 falls through

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Add the Security card to the `/settings` page from slice 09. The card lets a signed-in **User** change their password. On success the user stays on the page, the password fields clear, and a _"Password updated."_ toast appears. On a wrong previous password the user stays on the page with a generic toast (per the backend 400/401 split from issue 01 — without that split, this slice cannot be built honestly because 401 would log the user out).

**`useChangePassword()`** at `src/auth/useChangePassword.ts` — deep hook. Surface: `{ changePassword(input), isPending }`. Wraps `postApiV1AuthChangePasswordMutation()`. On success, fires the Sonner toast _"Password updated."_ and calls a caller-provided `form.reset()` (or `setValue` calls) to clear `previousPassword` and `proposedPassword`. On **400** (wrong previous password, post-contract-split from issue 01), the error falls through to the global generic toast — no inline disclosure per ADR-0007, no user-query invalidation. On **401** (truly not authenticated), the error falls through to the global 401 handler which invalidates the user query and triggers the redirect to `/login`. This is the correct behaviour because, post-split, 401 here genuinely means "session is dead".

**Security card** inside `<SettingsPage>` (added beneath the Profile card from slice 10):

- `<h2>` "Security".
- A `<form>` with two password fields: previousPassword (`autoComplete="current-password"`) and proposedPassword (`autoComplete="new-password"`).
- `useForm({ resolver: zodResolver(zChangePasswordRequest), shouldFocusError: true })`. Because the OpenAPI snapshot from issue 01 encodes the Cognito password policy on `ChangePasswordRequest.proposedPassword`, weak proposed passwords surface inline via the existing RHF + `zodResolver` pipeline. Same policy helper text under the new-password field as on `<RegisterCredentialsPage>` and `<ConfirmForgotPasswordPage>`.
- Update button "Update password" (shows "Updating…" while pending). Disabled while pending.
- On success: `form.reset()` clears both fields; toast fires.

No inline `setError('root', ...)` calls (per ADR-0007). The 400 case is observable to the user only via the generic toast; the 401 case redirects them to `/login`. The contract split from issue 01 is what makes this safe.

## Acceptance criteria

- [ ] `src/auth/useChangePassword.ts` exports `useChangePassword()` with `{ changePassword(input), isPending }`.
- [ ] On success, the hook fires the _"Password updated."_ toast and the caller's `onSuccess`-style callback (or `form.reset()` integration) clears the password fields.
- [ ] On 400, the hook does **not** install a per-call `setError`, does **not** invalidate the user query; falls through to the global generic toast. The user remains signed in.
- [ ] On 401, the hook does **not** install a per-call `setError`; the global handler from ADR-0001 invalidates the user query and the user is redirected to `/login`. Behaviour identical to any other 401 on a signed-in mutation.
- [ ] `<SettingsPage>` renders a Security card with `<h2>` "Security", two password fields with correct `autoComplete` tokens, and the policy helper text under the new-password field.
- [ ] Update button is disabled while pending; shows "Updating…" during the mutation.
- [ ] On success, both password fields are cleared.
- [ ] Client-side validation surfaces inline errors when proposedPassword violates `zChangePasswordRequest` (policy regex from the regenerated OpenAPI).
- [ ] No inline `setError('root', ...)` calls.
- [ ] Unit tests for `useChangePassword`: happy path fires success toast and clears the form; 400 falls through to the global toast and does **not** invalidate the user query (this is the load-bearing assertion for the 400/401 split — it must be tested); 401 invalidates the user query (matches global handler).
- [ ] Vitest UI test for the Security card: client-side policy validation surfaces inline errors; submit while pending state; on success fields clear.
- [ ] No E2E for this slice — would mutate the shared Cognito user's password; Vitest-only per the PRD's testing decisions.
- [ ] `/settings` (with the Security card visible) continues to pass the axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [01-backend-prereq-openapi-refresh.md](./01-backend-prereq-openapi-refresh.md)
- [09-appshell-dropdown-and-settings-stub.md](./09-appshell-dropdown-and-settings-stub.md)

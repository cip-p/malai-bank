# Register final submit: `useRegister` + post-register navigation to `/confirm-register?email=`

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Wire the third step of Registration up to the backend. After this slice, a brand-new user can fill in all three steps, press Create account, and land on `/confirm-register?email=<their email>` (the route itself is built in slice 05 — for this slice, a placeholder is acceptable but the navigation target URL must be correct).

**`useRegister()`** is a deep hook at `src/auth/useRegister.ts`. Surface: `{ register(input), isPending }`. Internally wraps the generated register mutation (`postApiV1AuthRegisterMutation()`). On success, navigates to `/confirm-register?email=<encoded input.email>`. On 4xx, the error is allowed to fall through to the global mutation `onError` handler, which surfaces the generic _"Something went wrong. Please try again."_ toast (per ADR-0007 — no inline disclosure of `UsernameExistsException` etc., to deny enumeration signal). No optimistic user-query writes — the just-registered User is **Unconfirmed** and cannot yet appear authenticated.

**`<RegisterCredentialsPage>`** is upgraded:

- Create account button is enabled when the form passes whole-schema validation.
- On submit, the page calls `form.trigger()` against the entire `RegisterRequest`. If valid, it pulls all the fields from the layout's form context and fires `useRegister().register(input)`.
- The button shows "Creating account…" with `disabled` while `isPending` is true.

Because the OpenAPI regenerated from issue 01 now encodes the Cognito password policy as `pattern` + `minLength` on `RegisterRequest.password`, weak passwords surface inline on step 3 via the existing RHF + `zodResolver` pipeline. Helper text under the password field lists the policy requirements (e.g. _"8+ characters, including a number, uppercase, lowercase, and a special character"_ — phrased to match whatever policy Cognito enforces). No strength meter; binary valid / invalid is sufficient.

The placeholder `<ConfirmRegisterPage>` for this slice is a stub at `src/pages/ConfirmRegisterPage.tsx` that renders an `<h1>` "Confirm your email" focused on mount, reads the `?email=` query for display only, and is otherwise empty. The real implementation lands in slice 05.

## Acceptance criteria

- [ ] `src/auth/useRegister.ts` exports `useRegister()` with the surface `{ register(input), isPending }`.
- [ ] `useRegister().register(input)` on success navigates to `/confirm-register?email=<URL-encoded input.email>`.
- [ ] `useRegister()` does **not** install a per-call `setError`-style inline message; 4xx falls through to the global toast handler (preserves ADR-0007's anti-enumeration policy).
- [ ] `<RegisterCredentialsPage>` Create account button is enabled when whole-form validation passes; while the mutation is pending the button shows "Creating account…" and is `disabled`.
- [ ] Submitting valid step-3 fields fires the mutation and on success navigates to `/confirm-register?email=...`.
- [ ] Password field on step 3 shows the policy requirements as helper text; client-side validation surfaces inline errors when the password violates the regenerated `zRegisterRequest` schema.
- [ ] `<ConfirmRegisterPage>` stub at `/confirm-register` renders an `<h1>` "Confirm your email" focused on mount; reads `?email=` for display. (Full implementation in slice 05.)
- [ ] Unit tests for `useRegister`: happy path navigates to `/confirm-register?email=...` (assert via spy or memory router); 4xx does not call any `setError` and does not invalidate the user query.
- [ ] Vitest UI-level test for `<RegisterCredentialsPage>` covers: submit-while-pending state, helper-text rendering, and that a 4xx response leaves the user on the page (no navigation).
- [ ] No E2E test for the register flow in this slice — the shared Cognito user cannot re-register and email-dependent flows are Vitest-only for this PRD (per the PRD's testing decisions).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [01-backend-prereq-openapi-refresh.md](./01-backend-prereq-openapi-refresh.md)
- [03-register-skeleton-layout-and-steps.md](./03-register-skeleton-layout-and-steps.md)

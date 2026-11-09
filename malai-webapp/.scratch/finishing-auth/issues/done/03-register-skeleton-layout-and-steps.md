# Register multi-step skeleton: `<RegisterLayout>` + step indicator + 3 step routes (no API call yet)

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Wire up the three-step **Registration** form's structural skeleton without yet firing the `POST /api/v1/auth/register` mutation. After this slice, a user can navigate `/register/about → /register/address → /register/credentials → /register/about` (back) with all fields preserved across step transitions, the step indicator updating, and per-step validation gating Continue. The final step's submit button does nothing yet — the mutation lands in slice 04.

**`<RegisterLayout>`** is a layout-route component at `src/pages/register/RegisterLayout.tsx`. It owns a single `useForm<RegisterRequest>({ resolver: zodResolver(zRegisterRequest), shouldFocusError: true })` instance and exposes it via a new `RegisterFormContext`. It renders `<RegisterStepIndicator step={…} />` above an `<Outlet />`. The current step is derived from `useLocation().pathname` (`about` → 1, `address` → 2, `credentials` → 3).

**`<RegisterStepIndicator step={1 | 2 | 3} />`** renders three labelled, numbered chips connected by arrows: `[✔ 1 About you] → [● 2 Address] → [   3 Credentials]`. Completed steps show a check, the current step is filled, future steps render plain. The container has `role="progressbar"`, `aria-valuenow={step}`, `aria-valuemin={1}`, `aria-valuemax={3}`, and an `aria-valuetext` like `"Step 2 of 3: Address"`. Uses only theme tokens (no literal Tailwind palette colors per the existing ESLint rule); any transition respects `motion-safe`.

**Three step pages:**

- `src/pages/register/RegisterAboutPage.tsx` (`/register/about`) — `<h1>` "About you" (focused on mount via `useFocusOnMount`), three fields (firstName, lastName, dateOfBirth — date input), a single Continue button. Continue calls `form.trigger(['firstName', 'lastName', 'dateOfBirth'])`; on success navigates to `/register/address`.
- `src/pages/register/RegisterAddressPage.tsx` (`/register/address`) — `<h1>` "Address", fields (line1, optional line2, city, postcode, country via the new `<CountrySelect>`), Back button (navigates to `/register/about`, never validates), Continue button (validates address fields, navigates to `/register/credentials`). Guards on entry: if any step-1 field is empty, `<Navigate to="/register/about" replace />`.
- `src/pages/register/RegisterCredentialsPage.tsx` (`/register/credentials`) — `<h1>` "Credentials", fields (email, password), Back button (navigates to `/register/address`), Create account button — **disabled in this slice; no API call yet, no navigation**. Guards on entry: if any step-1 or step-2 field is empty, `<Navigate to="/register/about" replace />`.

**`<CountrySelect>`** at `src/components/CountrySelect.tsx` — a disabled shadcn `<Select>` whose only option is `GB` ("United Kingdom"), pre-selected. RHF-compatible props.

Routes are declared under the existing `<Unauthenticated>` parent from issue 02. Specifically: `<Route element={<Unauthenticated />}><Route path="/register" element={<RegisterLayout />}><Route path="about" .../><Route path="address" .../><Route path="credentials" .../></Route></Route>`. A bare `/register` redirects to `/register/about`.

Per ADR-0004, every step's validation goes through `zRegisterRequest` (via `form.trigger(...)`). No hand-authored Zod schema is introduced.

## Acceptance criteria

- [ ] `src/pages/register/RegisterLayout.tsx` owns a single `useForm<RegisterRequest>` instance and exposes it via `RegisterFormContext`. A `useRegisterFormContext()` hook throws an `InvariantViolation` if used outside the provider.
- [ ] `src/components/RegisterStepIndicator.tsx` renders the labelled progress chip pattern with `role="progressbar"`, correct ARIA values, and `aria-valuetext`. Uses theme tokens only; any transition respects `motion-safe`.
- [ ] Routes declared under `<Unauthenticated>`: `/register` (redirects to `/register/about`), `/register/about`, `/register/address`, `/register/credentials`. Each is rendered inside `<RegisterLayout>`.
- [ ] `<RegisterAboutPage>`, `<RegisterAddressPage>`, `<RegisterCredentialsPage>` each render their `<h1>` (focused on mount via `useFocusOnMount`) with their respective headings.
- [ ] `<RegisterAddressPage>` includes a `<CountrySelect>` that is disabled and pre-selected to `GB`.
- [ ] Continue on step 1 validates step-1 fields only and navigates to `/register/address` on success.
- [ ] Continue on step 2 validates step-2 fields only and navigates to `/register/credentials` on success.
- [ ] Back on steps 2 and 3 navigates without validation.
- [ ] Step 2 entry-guard: hitting `/register/address` with empty step-1 data redirects to `/register/about` with `replace`.
- [ ] Step 3 entry-guard: hitting `/register/credentials` with empty step-1 or step-2 data redirects to `/register/about` with `replace`.
- [ ] Step 3's Create account button renders but is disabled in this slice. (The mutation lands in slice 04.)
- [ ] Focus moves to each step's `<h1>` after Continue / Back navigation.
- [ ] Unit tests cover: `useRegisterFormContext()` outside provider throws; step-1 trigger validates only step-1 fields; step-2 entry-guard redirects when step-1 data missing.
- [ ] Unit test for `<RegisterStepIndicator>` covers the ARIA semantics across all three step values.
- [ ] `/register/about`, `/register/address`, `/register/credentials` added to the existing axe a11y spec; zero violations on `malai` theme (light + dark) — note the progressbar role is the new pattern.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [02-unauthenticated-layout-and-login-email-prefill.md](./02-unauthenticated-layout-and-login-email-prefill.md)

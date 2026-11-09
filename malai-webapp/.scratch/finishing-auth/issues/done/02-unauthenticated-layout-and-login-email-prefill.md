# `<Unauthenticated>` layout route + LoginPage `?email=` pre-fill

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

This is the foundational slice for every new public auth route in this PRD. It introduces a new layout-route component that mirrors `<Authenticated>` for the signed-out side, and teaches `<LoginPage>` to accept an `?email=` query pre-fill (needed by post-confirm and post-reset navigation in later slices).

**`<Unauthenticated>`** is a layout-route component at `src/auth/Unauthenticated.tsx`. It reads `useUser()` (the deep hook, single source of truth per ADR-0001) and:

- renders a full-page skeleton — the same shape `<Authenticated>` renders today — while `status === 'unknown'`,
- renders `<Navigate to="/" replace />` when `status === 'authenticated'`,
- renders `<Outlet />` when `status === 'unauthenticated'`.

`<LoginPage>` reads `?email=` from `useSearchParams()` and uses it as the `defaultValue` for the email field. Empty / missing query → empty field as today. The redundant `useEffect → navigate` for signed-in users currently inside `<LoginPage>` is removed — the `<Unauthenticated>` layout now handles that responsibility for every public route uniformly.

The Router (`App.tsx`) is restructured so `/login` lives under `<Unauthenticated>`. Subsequent issues (03, 05, 06) drop their routes under the same layout without re-implementing the redirect.

## Acceptance criteria

- [ ] `src/auth/Unauthenticated.tsx` exists and exports a `<Unauthenticated>` component that reads `useUser()` and renders skeleton / Navigate-to-`/` / Outlet by the three-status branch above.
- [ ] `App.tsx` wraps `/login` in a `<Route element={<Unauthenticated />}>` parent, leaving `<Authenticated>`-protected routes untouched.
- [ ] `<LoginPage>` accepts an `?email=` query parameter and uses it as the initial value of the email field. The redundant `useEffect → navigate` for signed-in users is removed; behaviour is now provided by `<Unauthenticated>`.
- [ ] Unit tests for `<Unauthenticated>` cover all three status branches (unknown → skeleton; authenticated → redirects with replace; unauthenticated → renders children).
- [ ] Unit test for `<LoginPage>` confirms email field pre-fills from `?email=jane%40example.com` (URL-decoded).
- [ ] E2E test: with the shared signed-in `storageState`, visit `/login`, assert redirect to `/`. (Confirms the layout's signed-in branch.)
- [ ] E2E test: without `storageState`, visit `/login?email=jane%40example.com`, assert the email field is pre-filled with `jane@example.com`.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

None — can start immediately.

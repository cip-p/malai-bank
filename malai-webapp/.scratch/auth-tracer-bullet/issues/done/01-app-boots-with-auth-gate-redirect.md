# App boots; unauthenticated `/` redirects to placeholder `/login`

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Auth tracer-bullet: login → list of Accounts

## What to build

The Vite app boots into a routable React 19 + React Router 7 shell with the architectural foundation in place. Visiting `/` while unauthenticated redirects to a placeholder `/login` page that renders only `<h1>Sign in</h1>` for now — the real form lands in issue 02. The redirect mechanism is the `<Authenticated>` layout route observing `useUser()`; the user query is wired against `GET /api/v1/user`.

A `QueryClient` is configured at the app root with two global hooks: any query/mutation returning 401 invalidates the user query (so the gate observes and redirects in one place — no per-query 401 logic), and any unhandled mutation error surfaces a generic Sonner toast ("Something went wrong. Please try again."). The `malai` theme is the only named theme defined in `src/styles/themes.css` under `[data-theme="malai"]`, with the shadcn token surface populated. `<html data-theme="malai">` is set in `index.html` to avoid an FOUC on first paint. The ESLint configuration gains a rule banning literal Tailwind palette colour utilities (`bg-{color}-{shade}`, `text-{color}-{shade}`, `border-{color}-{shade}`, `ring-{color}-{shade}`, etc.) across the full palette, with `inherit` / `current` / `transparent` as the only exceptions. Token utilities (`bg-primary`, `text-muted-foreground`, …) remain allowed.

Per ADR-0001 the gate uses TanStack Query as the single source of auth state. Per ADR-0002 it does not use React Router loaders. Per ADR-0003 no literal palette colours leak into any component. Per CONTEXT.md this slice is auth-only — no AccountHolder or Account data is fetched yet.

## Acceptance criteria

- [ ] `npm run dev` boots the app on port 5173 against the local backend at `:8080`.
- [ ] Visiting `/` while unauthenticated redirects to `/login`.
- [ ] `/login` renders an `<h1>` with the text "Sign in" (no form yet).
- [ ] `useUser()` returns `{ user, status: 'unknown' | 'authenticated' | 'unauthenticated' }` and is the only place in the codebase that reads auth state.
- [ ] `<Authenticated>` layout route is implemented and wraps the route tree's protected branch.
- [ ] The `QueryClient` global handler invalidates the user query on any 401 from any query or mutation.
- [ ] The `QueryClient` global mutation `onError` surfaces a generic Sonner toast for unhandled errors.
- [ ] `src/styles/themes.css` defines `[data-theme="malai"]` with the shadcn token surface; `index.html` sets `<html data-theme="malai">`.
- [ ] The ESLint rule banning literal Tailwind palette colours is in the config and fires on a deliberate violation, exiting `npm run lint` non-zero.
- [ ] Vitest unit specs cover `useUser` status mapping under pending / success / 401 / other-error.
- [ ] A Playwright spec asserts that an unauthenticated visit to `/` redirects to `/login`; the spec opts out of `playwright/.auth/user.json`.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, and `npm run build` all pass.

## Blocked by

None — can start immediately.

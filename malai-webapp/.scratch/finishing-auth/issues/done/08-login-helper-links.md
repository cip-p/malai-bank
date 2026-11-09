# LoginPage three helper links: Sign up / Confirm / Forgot

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

`<LoginPage>` gains three helper links beneath the form so users can navigate into each public auth flow without typing URLs by hand. This is the slice that finally connects the signed-out journeys into one navigable web.

Below the existing form (after the Sign in submit button), render a small section with three links, in this order:

1. **"Forgot your password?"** → `/forgot-password`
2. **"Don't have an account? Sign up"** → `/register/about`
3. **"Already registered but haven't confirmed? Confirm your account"** → `/confirm-register`

All three render as React Router `<Link>` components, styled as the muted link variant available in the existing theme tokens (no literal palette colors per the ESLint rule). Each is a normal focusable element in the page's tab order; the existing `<h1>` focus-on-mount behaviour from ADR-0006 is untouched.

The third link does **not** pass an `?email=` query — the closed-tab re-entry case implies the user is starting fresh; they type both fields on `/confirm-register`.

No new hook, no new mutation. This is a pure navigation / copy slice.

## Acceptance criteria

- [ ] `<LoginPage>` renders three `<Link>` components beneath the form, in the order specified above, with the exact copy specified above.
- [ ] Each link navigates to its target route on click and via keyboard activation.
- [ ] Link styling uses theme tokens (no literal Tailwind palette utilities). The ESLint rule continues to pass.
- [ ] The existing focus-on-mount and form behaviours on `<LoginPage>` are unchanged.
- [ ] Vitest UI test for `<LoginPage>` covers: all three links are rendered with the exact copy and `href`s; each link is focusable in tab order.
- [ ] E2E test: without `storageState`, visit `/login`, click each of the three links in turn, assert the URL transitions to `/forgot-password`, `/register/about`, and `/confirm-register` respectively.
- [ ] `/login` continues to pass the existing axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [04-register-submit-and-useregister.md](./04-register-submit-and-useregister.md)
- [05-confirm-register-page.md](./05-confirm-register-page.md)
- [06-forgot-password-page.md](./06-forgot-password-page.md)

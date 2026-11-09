# Auth tracer-bullet: login → list of Accounts

Status: ready-for-agent

## Problem Statement

A bank decision-maker watching the malai demo — or registering and exploring on their own — needs to land in something that already looks and feels like a production UK retail bank within seconds of opening the app. Today `malai-webapp/src/` is empty: no entry point, no signed-in experience, no signed-out experience, no theming chrome, nothing to demo. The skeleton has to exist before any of the more nuanced banking features can be shown.

For a User who already has a Cognito identity (e.g. the shared demo user used by the existing Playwright setup), the moment of value is: sign in, see "this is my bank." If that single round-trip is broken, slow, off-brand, or inaccessible, every subsequent demo decision is undermined.

## Solution

Build the smallest end-to-end vertical slice that exercises the entire architectural skeleton:

- A `/login` page that accepts email + password and authenticates against the existing backend.
- A signed-in landing page at `/` that lists the **Accounts** belonging to the User's **Active AccountHolder**.
- An auth gate that routes unauthenticated visitors to `/login` and authenticated visitors past it.
- A signout affordance that ends the session cleanly and returns the User to `/login`.
- The shadcn CSS-variable theming machinery in place from day one, plus the ESLint discipline banning literal Tailwind palette colors, so a sales engineer can later rebrand the app by editing one tokens file.
- The a11y baseline from ADR-0006 wired up so the demo can withstand bank-grade scrutiny.

The slice exercises every load-bearing decision from the grilling session exactly once: routing, the `<Authenticated>` gate, TanStack Query as the source of auth state, no-loaders data fetching, the generated-Zod form-validation pipeline, the generic-error UX with the one wrong-password exception, holder-header injection on holder-scoped reads, theming-by-tokens, and the a11y rules. After this slice lands, every remaining auth feature (Registration, confirm-register, forgot-password, change-password) is "another form against another endpoint" — same patterns, just iteration.

## User Stories

1. As a User with an existing Cognito identity, I want to navigate to the app at `/` and be sent to `/login` if I am not signed in, so that the app is unambiguous about what it expects from me.
2. As a User on the login page, I want a clearly-labelled email and password form, so that I know exactly what to provide.
3. As a User on the login page, I want client-side validation to catch obviously bad input (empty field, malformed email) before I submit, so that I do not wait for a network round-trip to discover I missed a field.
4. As a User on the login page, I want validation errors to appear inline next to the field they describe, so that I can correct them without scanning the page.
5. As a User on the login page using a screen reader, I want validation errors to be announced and the first offending field to receive focus after a failed submit, so that I do not have to hunt for what went wrong.
6. As a User submitting valid credentials, I want my session established and to land directly on the list of my **Accounts**, so that I see the value the app delivers without an extra click.
7. As a User entering the wrong password, I want a clear inline message — _"Incorrect email or password."_ — displayed prominently above the form, so that I know exactly what to retry.
8. As a User whose login fails for any reason other than wrong credentials (5xx, network down, Cognito unavailable), I want a non-blocking generic message that something went wrong, so that I am not led to believe my credentials were the problem.
9. As a User signed in and on the landing page, I want to see every **Account** that belongs to my **Active AccountHolder**, so that I can survey my financial relationships with malai.
10. As a User signed in, I want the **Accounts** list to load with a skeleton state rather than a blank screen, so that the app feels responsive on a slow connection.
11. As a User signed in, I want my display name visible in the page chrome, so that I have confidence the right session is active.
12. As a User signed in, I want a visible "Sign out" control that ends my session and returns me to `/login`, so that I can end the demo cleanly.
13. As a User whose session expires mid-use (a 401 from any background query), I want to be returned to `/login` automatically without surprise, so that I do not interact with stale data.
14. As a User navigating with the keyboard only, I want a "Skip to main content" link to appear as the first focusable element on every page, so that I can bypass repeated chrome.
15. As a User using a screen reader, I want keyboard focus to land on the new page's `<h1>` after every navigation, so that I hear what page I am now on.
16. As a User with `prefers-reduced-motion` enabled in my OS, I want every transition and animation in the app to be disabled, so that I do not get nauseous from motion the app could have politely suppressed.
17. As a bank decision-maker watching the demo, I want the entire login → accounts flow to render only from theme tokens (never literal colors), so that swapping a single tokens file demonstrably re-skins every screen.
18. As a sales engineer preparing a demo for a specific prospect, I want to change one `[data-theme="..."]` block to ship a bespoke theme without touching any component, so that I can rebrand within an hour.
19. As a bank decision-maker watching the demo, I want every page to satisfy WCAG 2.1 AA contrast under the active theme, so that the demo could pass an accessibility audit on its own merits.
20. As a developer regenerating the API client after a contract change, I want every form whose endpoint shape changed to fail to type-check, so that drift between client and server validation is impossible at compile time.
21. As a developer reading the codebase, I want a single, obvious place to ask _"is the User signed in?"_ — the `useUser()` hook — so that I do not have to track parallel auth state across multiple stores.
22. As a developer reading the codebase, I want the **Active AccountHolder** selection encapsulated in a small explicit hook with a clear invariant (returns a non-null Individual holder or throws), so that I do not have to handle a `null` empty state that contradicts the domain.
23. As a developer adding a new protected page later, I want only to drop a route under `<Authenticated>` and have the gate apply automatically, so that I cannot forget to authenticate a route by accident.
24. As a developer running tests, I want unit tests for each deep hook and Playwright tests for both the happy and sad paths, so that the tracer-bullet's load-bearing rules cannot regress silently.
25. As a User who has signed out, I want my session cleared such that re-visiting `/` does not pick up a stale signed-in state, so that signout is a real exit and not a façade.
26. As a developer hand-editing `themes.css`, I want the ESLint rule banning literal Tailwind palette colors to fire immediately if a component leaks `bg-blue-500` or similar, so that the rebrand discipline does not rot.

## Implementation Decisions

This PRD enacts the six ADRs already on the branch (`docs/adr/0001` through `0006`) and the additional grilling decisions that live in the conversation around them. The PRD does not re-litigate any of these; it commits to them as the contract for implementation.

The architectural shape:

- **`useUser()`** is a deep hook. Its narrow surface is `{ user: UserResponse | null, status: 'unknown' | 'authenticated' | 'unauthenticated' }`. Internally it wraps the generated `getUserOptions()` and maps the underlying TanStack Query state — `pending → 'unknown'`, success → `'authenticated'`, 401 → `'unauthenticated'`, other errors → still `'unknown'` (treat unknown-status errors as transient). This hook is the **only** place in the codebase that reads auth state.
- **`useLogin()`** is a deep hook. Its surface is `{ login(input), isPending }`. Internally it wraps the generated login mutation; `onSuccess` invalidates the user query (so the `<Authenticated>` gate observes the new state on next render); `onError` for HTTP 401 calls a caller-provided RHF `setError` with `('root', { message: 'Incorrect email or password.' })`; every other error is allowed to bubble so the generic toast handler can surface it.
- **`useSignout()`** is a deep hook. Its surface is `{ signout, isPending }`. Internally it wraps the generated signout mutation; `onSuccess` clears the relevant queries (the user query, holders query, accounts query) so a subsequent navigation cannot read stale data.
- **`useActiveAccountHolder()`** is a deep hook. Its surface is `{ activeHolderId, holder, status }`. Internally it reads `/api/v1/account-holder/all` via the generated query, then derives the active holder as `.find(h => h.accountHolderType === 'INDIVIDUAL')`. A null result after the query resolves throws an `InvariantViolation` (per CONTEXT.md: every signed-in User has exactly one Individual holder). A Zustand slice `useActiveHolderOverrideStore` is in place but always null in v1; the hook returns the override when set, falling back to the Individual derivation otherwise. This is the architectural seam for the future switcher without committing to switcher UI now.
- **`useAccounts()`** is a thin wrapper over `getAccountsOptions(...)` that injects the `MalAccountHolderIdHeader` from `useActiveAccountHolder()`. Pages never see the header; the header rule lives in exactly one place.

The composition layer:

- **`QueryClient`** is created with a global error handler at the cache level (queries and mutations both). The handler inspects the response status; on 401 it calls `queryClient.invalidateQueries({ queryKey: getUserOptions().queryKey })`. That is the **only** redirect mechanism — the gate observes the invalidated user query and navigates. Individual queries never own redirect logic.
- **`<Authenticated>`** is a layout route component. It reads `useUser()` and:
  - renders a full-page skeleton while `status === 'unknown'`,
  - renders `<Navigate to="/login" state={{ from }} />` when `status === 'unauthenticated'`,
  - renders `<AppShell><Outlet /></AppShell>` when `status === 'authenticated'`.
- **`<AppShell>`** is the chrome wrapping every protected page. It contains the **Skip to main content** link as its first focusable element, a `<main id="main" tabIndex={-1}>` landmark, the signed-in User's display name, a Sign out button (driven by `useSignout`), and a theme switcher control that is rendered as disabled in v1 (the switcher pattern exists so adding the second theme is an N-line change).
- **`useFocusOnMount(ref)`** is a small hook that, on mount, calls `.focus({ preventScroll: false })` on the ref'd element. Pages apply it to their `<h1>`, which has `tabIndex={-1}`. This is the entirety of the focus-management implementation from ADR-0006.
- **`<LoginPage>`** renders `<h1>Sign in</h1>` (focused on mount), an email field, a password field, a submit button, and a root-error region. The form uses `useForm({ resolver: zodResolver(zLoginRequest), shouldFocusError: true })` and submits via `useLogin().login(...)`. The root-error region renders `errors.root?.message` when present.
- **`<AccountsPage>`** renders `<h1>Your accounts</h1>` (focused on mount), the **Active AccountHolder**'s display name beneath the heading, and the list of **Accounts** for that holder. A skeleton renders while `useAccounts` is loading. Per-Account row content is the Account's display name plus its identifiers (sort code / account number) if cheaply available from the list response; balance, transactions, statements, and any per-Account drill-in are out of scope for this PRD.
- **Router** declares two route trees: a public tree containing `/login`, and a protected tree under `<Authenticated>` containing `/` (the Accounts page). React Router 7 is configured **without** loaders per ADR-0002.
- **Theming**: a single named theme (`malai`) is defined in `src/styles/themes.css` under `[data-theme="malai"]`. `next-themes` is configured to manage light/dark on top. `<html data-theme="malai">` is set in `index.html` so there is no flash of unstyled content on first paint. The theme switcher control in `<AppShell>` is wired to swap the attribute but is disabled in v1 because there is only one named theme.
- **Lint rule**: the ESLint config gains a Tailwind class-restriction rule that bans literal palette utilities (`bg-{color}-{shade}`, `text-{color}-{shade}`, `border-{color}-{shade}`, `ring-{color}-{shade}`, etc.) across the full Tailwind palette except `inherit`, `current`, `transparent`. Token utilities like `bg-primary`, `text-muted-foreground`, `border-border` remain allowed.
- **Error handling**: a global mutation `onError` hook in the QueryClient defaults shows a generic Sonner toast (_"Something went wrong. Please try again."_) for every uncaught error. The login mutation's per-call `onError` consumes the 401 before the global handler observes it, so the toast does not fire on bad-password.

Backend contract dependencies (no changes required for this PRD):

- `POST /api/v1/auth/login` — used by `useLogin`. Sets HttpOnly cookies; the response body tokens are ignored per the OpenAPI guidance.
- `GET /api/v1/user` — used by `useUser` as the single source of truth for auth state.
- `GET /api/v1/account-holder/all` — used by `useActiveAccountHolder` to derive the Individual holder.
- `GET /api/v1/accounts` (requires `MalAccountHolderIdHeader`) — used by `useAccounts`.
- `POST /api/v1/auth/signout` — used by `useSignout`.

If any of these contracts change while implementing, refresh `./openapi.yaml`, run `npm run openapi:gen`, and chase the TypeScript errors per the webapp `CLAUDE.md` workflow.

## Testing Decisions

What makes a good test in this codebase:

- Tests assert externally observable behaviour, not implementation details. `useUser` is tested via its public return value under each underlying query state — not by inspecting which internal hook it composes. `useLogin` is tested via what it calls on `setError` and the query client — not by intercepting its internal `useMutation`.
- Tests reuse the OpenAPI-defined types and the generated Zod schemas to construct fixtures. A contract change makes invalid fixtures fail to compile rather than silently passing the wrong shape into a test.
- E2E tests drive the real local webapp against the real local backend per `malai-webapp/CLAUDE.md` (no MSW or mocks in E2E; no Playwright `webServer` auto-spawning). They reuse the shared Cognito user and Playwright `storageState` from `tests/e2e/auth.setup.ts`, except where the test is about unauthenticated behaviour (in which case it explicitly opts out of the saved storage state).
- No comments in any test file. Test intent comes from `describe`/`it` names, fixtures, and assertions.

Unit tests (Vitest, `npm test`):

- **`useUser`**: assert `status` is `'unknown'` while the underlying query is pending; `'authenticated'` when it resolves with a user; `'unauthenticated'` on a 401 error; `'unknown'` again on any non-401 error. Verify the returned `user` mirrors the query data exactly when authenticated.
- **`useLogin`**: assert that on HTTP 401 it calls the provided `setError` with `('root', { message: 'Incorrect email or password.' })` and does not invalidate the user query; on success it invalidates the user query and does not call `setError`; on other errors it neither invalidates nor calls `setError` (the generic toast path handles those).
- **`useActiveAccountHolder`**: assert it returns the first Individual holder when the holders response contains multiple types in any order; assert it throws `InvariantViolation` when the response contains no Individual holder (post-resolution); assert `status === 'unknown'` while the underlying query is pending and `status === 'error'` on a non-resolvable error.

E2E tests (Playwright, `npm run test:e2e`):

- **Happy path** — using the shared signed-in storageState: visit `/`, assert the Accounts page renders with the active holder's display name and at least one Account row. Smoke-cover the focus-on-h1 behaviour by asserting `document.activeElement` matches the page heading immediately after navigation.
- **Sad path: wrong password** — explicitly without storageState. Visit `/login`, fill the form with an invalid password, submit, assert the inline message _"Incorrect email or password."_ appears above the form and that no toast appears.
- **Sad path: unauthenticated redirect** — without storageState. Visit `/`, assert redirect to `/login`.
- **Round-trip: signout** — using the shared signed-in storageState: visit `/`, click "Sign out", assert redirect to `/login`. Then visit `/` again in the same context, assert a second redirect to `/login` (proving the session was actually cleared, not just the UI).

Accessibility (`npm run a11y`, which runs `tests/e2e/a11y.spec.ts`):

- Add both `/login` (unauthenticated) and `/` (authenticated, via storageState) to the existing axe spec. Both must pass with zero violations on the default `malai` theme in both light and dark modes.

Prior art:

- `tests/e2e/auth.setup.ts` already exists and provides the shared sign-in via the API + writes Playwright `storageState`. The `e2e` Playwright project depends on the `setup` project; most authenticated specs should consume the storageState from `playwright/.auth/user.json`. The two sad-path specs are the explicit exception — they opt out of the storage state.
- `tests/e2e/a11y.spec.ts` already exists as the home for axe-core scans.
- `src/test/setup.ts` already exists as the Vitest setup. The first unit test written under this PRD becomes the pattern reference for the rest — there is no prior unit-test pattern in this repo to imitate.

## Out of Scope

- Registration (`POST /api/v1/auth/register`), confirm-register, resend-confirmation-code — separate PRD.
- Forgot-password and confirm-forgot-password — separate PRD.
- Change-password — separate PRD.
- The **AccountHolder** switcher UI — deferred until a demo scenario surfaces a second holder type per the question-5 decision. The Zustand override slice exists; the chip / menu does not.
- Per-Account balance loading, transaction list, account statements, confirmation-of-funds, account detail page — out of scope for the skeleton; subsequent PRDs.
- Transfers, payees / confirmation-of-payee, direct-debits, standing-orders — out of scope; subsequent PRDs.
- A second named theme. The mechanism is in place; the catalogue grows when the first sales-engineer rebrand actually happens.
- Automated WCAG contrast linting. v1 relies on manual review of each theme's token set against AA contrast.
- Workspace tooling that generates the `MalaiCode → human copy` table from the backend Kotlin enum. The generic-message rule is in force; revisit when the second demo-grade error case arrives (ADR-0005).
- i18n / translation. UI copy is English-only for v1.
- The folder layout decision (`src/features/<x>/` vs flat) — left intentionally to be forced by the implementation itself, in line with the webapp `CLAUDE.md` rule against generalising from one.
- `QueryClient` defaults like `staleTime`, `refetchOnWindowFocus`, `retry` — pick values during implementation as the tracer-bullet actually surfaces a behaviour to tune.

## Further Notes

- The webapp `CLAUDE.md` elevates `/tdd` for this repo specifically — _"webapp is the right place to make this habit."_ The natural starting move for this PRD is to write the failing login E2E happy-path spec first, then drive the login form into existence, then drive the accounts page into existence, then back-fill unit tests as the deep hooks emerge.
- The Cognito password policy ↔ OpenAPI `pattern` / `minLength` synchronisation flagged in ADR-0004 is a workspace concern — it does not bite this PRD because login does not validate password strength (only that something was entered). It will bite the Registration PRD; flagging now so the workspace coordinates an answer before that PRD lands.
- The `<Authenticated>` gate stashes the originally-requested location in router state when it redirects to `/login`. A future post-login redirect-to-original feature is therefore a one-line read inside the login `onSuccess`; for v1 the post-login destination is unconditionally `/`.
- All copy in this PRD ("Sign in", "Your accounts", "Incorrect email or password.", "Something went wrong. Please try again.", "Skip to main content", "Sign out") is the v1 contract. Subsequent PRDs may revisit it but should not silently diverge.

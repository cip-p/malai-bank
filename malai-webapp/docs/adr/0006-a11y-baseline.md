# A11y baseline for the webapp

UK retail banks are regulated under the Equality Act 2010 and FCA expectations for digital services, both of which target WCAG 2.1 AA. Even though malai-webapp is a sales demo of a bank rather than a production bank, the audience for the demo is bank executives and technologists — they will judge a11y against the same bar as their own products. We therefore commit to the following as project rules from day one, before the codebase grows past a single page:

1. **Focus moves to the new page's `<h1>` on route change**, via a `useFocusOnMount` hook applied at each page component. Every page has exactly one `<h1>` (the page title), focusable via `tabIndex={-1}`. A "Skip to main content" link, visible only when focused, appears at the top of the shell layout. Caught by axe-core in `tests/e2e/a11y.spec.ts` if violated.

2. **Form-submit errors focus the first invalid field** (React Hook Form's `shouldFocusError: true` default). Each form field has `aria-invalid` (driven by RHF field state) and `aria-describedby` pointing at the error message. Server-side form errors via `setError('root', ...)` focus the form-level error container instead of any single field.

3. **All transitions and animations respect `prefers-reduced-motion: reduce`** via Tailwind v4's `motion-safe` / `motion-reduce` variants. No animation runs unconditionally.

4. **Every theme token meets WCAG AA contrast against its paired background token.** For v1 this is a manual review per named theme before merge — when the theme catalogue grows past 2–3, introduce automated contrast linting (axe alone won't cover every token pair). See ADR-0003 for the theming model the tokens belong to.

5. **Sonner toasts are the only live region in v1.** No additional `aria-live` regions until a feature needs an announcement that toast doesn't cover (e.g. real-time balance updates, transaction stream notifications). The first such need triggers reconsideration of whether a polite live region belongs at the shell level.

## Why an ADR for what looks like coding conventions

Each of these decisions is hard to retrofit. Sprinkling focus-on-h1 across 30 existing pages after the fact is meaningfully harder than enforcing it from page one; the same for `motion-safe` discipline and contrast-on-tokens. Capturing them now means a contributor adding a new page knows the contract before the second example exists to imitate.

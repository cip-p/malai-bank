# Three sibling Surfaces: Banking stays at root, /ops and /exec are role-offered

The MDR back office and the executive dashboard join the customer app as sibling **Surfaces** of one SPA, reached by one registered login wearing every hat (the demo policy: registration grants `admin` alongside `customer`; a real bank deploying this simply doesn't grant the extra groups). The client brief proposed moving the customer app under `/webapp` beside `/opsview` and `/manager-dashboard`. The accepted shape instead: **the Banking surface keeps `/`**, and the two new Surfaces are **`/ops`** (the MDR back office) and **`/exec`** (executive numbers).

Offering is presentation, enforcement is the API's: the frontend reads `roles` from `GET /api/v1/user` (resolved from the JWT's Cognito groups, never the database) and uses it only to decide which Surfaces to offer. `CUSTOMER` offers Banking, `ADMIN` offers Ops and Exec, `SUPPORT_AGENT` offers nothing yet — stated, not accidental. Post-login **Landing** is the first offered Surface in precedence `/` → `/ops` → `/exec`; a User with no offered Surface lands on the **Access page**. A withheld Surface renders the Access page — a calm "this area isn't part of your access" with links to the Surfaces the User does hold — never the NotFound page and never a silent redirect.

## Considered options

- **Move the customer app under `/webapp` (the brief's proposal).** Rejected. Because the three Surfaces are siblings, there is no common base path, so the move cannot ride Vite `base` + router `basename` — it means literal route prefixes, touching every path literal in `src/`, ~30 unit test files, all 24 Playwright specs plus the a11y sweep, a redirect layer for live deep links, and Sarah's demo script (URLs are part of a script — a hard constraint says it must not need editing). The sibling symmetry buys nothing an evaluator can see: with Banking at `/` and two roots beside it, the "one login, three hats" story demos identically. It also matches how real UK banks ship — the customer product owns the root.
- **`/opsview` as the ops root name.** Rejected: Opsview is a commercial UK IT-monitoring product (now ITRS) — a live naming collision for exactly the audience malai demos to. `/ops` is shorter, calmer, collision-free, and matches the existing "ops reviewer" persona language.
- **`/manager-dashboard` as the exec root name.** Rejected: names neither the persona nor the UK-banking concept (MI), and is long in a URL bar during an over-the-shoulder demo. `/exec` names the persona and reads instantly. `/mi` was considered and rejected as too cryptic.
- **Withhold by silent redirect or by NotFound.** Both rejected: a silent redirect is polite but mysterious (you typed a URL, something else appeared), and NotFound-for-forbidden is a real-bank privacy posture wasted on a demo whose point is that the role model is real and legible.
- **Landing always `/`, redirecting non-customers onward.** Rejected: the staff-only login (a scripted demo beat) would visibly land on the wrong Surface first.

## Consequences

- The `Authenticated` guard's two jobs — proving the session and rendering the customer chrome — must split: Ops and Exec need the first without the second. `AppShell`, the Active-AccountHolder machinery, and the `MAL-Account-Holder-Id` header discipline become Banking-surface concerns, not app-wide ones.
- The CONTEXT.md invariant "every signed-in User has exactly one Individual AccountHolder" is falsified by staff-only Users (the seeded ops reviewer has no customer estate by design) and is rescoped to Users holding `CUSTOMER`.
- The Access page is a designed state, distinct from NotFound, and the customer-only-token gating case is proved by a Playwright test against the real 403s, not asserted in a document.
- Frontend role logic never gates data access — every `/api/v1/admin/*` call is enforced server-side (403 for non-admin JWTs) regardless of what the UI offers.
- Existing routes, deep links, bookmarks, demo scripts, and all current tests survive unchanged.

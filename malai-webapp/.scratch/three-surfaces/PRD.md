# PRD — Three Surfaces: Banking, Ops, Exec

Status: ready-for-agent

Grilled 2026-07-18 against `client-brief.md`. Decisions recorded in ADR-0018 (sibling Surfaces, roots, offering model) and ADR-0019 (staff surfaces pinned outside the Theme catalogue); language in `CONTEXT.md` (§Surfaces and Roles, §Ops surface, §Exec surface). The MDR domain language is owned by `malai-backend/malai-services/mdr-service/CONTEXT.md`.

## Problem Statement

Malai's real user is a bank evaluator who registered to explore. The platform now has a Model Decision Record — an append-only, hash-chained audit plane for AI-assisted decisions, live on the backend with a seeded worklist and a seeded ops reviewer — and none of it is visible. Making evaluators juggle separate logins to see the back office costs demos. One registered person needs to reach the customer app, the MDR back office, and the executive numbers with one login — while the role model stays real: a bank deploying this for real simply doesn't grant the extra groups, and everything still enforces correctly.

## Solution

One SPA, three sibling **Surfaces** behind the existing login: the **Banking surface** stays at `/` unchanged; the **Ops surface** (`/ops`) exposes the MDR worklist, decision replay, rulings, the actionable-output gate, and chain verification as a calm, dense, keyboard-friendly UK-bank ops tool; the **Exec surface** (`/exec`) ships as a designed shell whose metric tiles are honest about the pending metrics API. The frontend reads `roles` from the user profile and uses it only to **Offer** surfaces — landing each user on the first Offered Surface (`/` → `/ops` → `/exec`) and rendering a polite **Access page** for Withheld ones. Enforcement stays at the API: the demo policy grants every hat at registration; the hats themselves remain real.

## User Stories

1. As a bank evaluator who registered to explore, I want my one login to reach the customer app, the back office, and the executive view, so that I see the whole platform without juggling accounts.
2. As a bank evaluator holding the customer and admin hats, I want to land on the Banking surface after login exactly as today, so that the primary product experience is unchanged.
3. As a bank evaluator, I want the Ops and Exec surfaces listed in the user menu where Sandbox already lives, so that I discover the other hats without the customer app growing staff furniture.
4. As a bank evaluator on the Ops surface, I want a link back to Banking (and across to Exec), so that I can move between hats without editing the URL.
5. As an ops reviewer, I want `/ops` to open on the worklist filtered to decisions awaiting a ruling, so that my default view is the work that needs me.
6. As an ops reviewer, I want the worklist newest-first with each decision's Outcome and Ruling in force readable at a glance, so that I can triage without opening rows.
7. As an ops reviewer, I want to filter the worklist by ruling state (awaiting / accept / edit / reject), so that I can audit any slice of the record.
8. As an ops reviewer, I want each worklist row to show the model, latency, cost, correlation reference, and how many dispositions it has accrued, so that I can judge weight before opening it.
9. As an ops reviewer, I want the Decision view to replay the rendered prompt, retrieved-context references with content hashes, raw output, and verdict exactly as recorded, so that I can reconstruct what the model saw and produced.
10. As an ops reviewer, I want the full disposition thread in chain order on the Decision view, so that I can see every ruling ever made, not just the one in force.
11. As an ops reviewer, I want an edit ruling's diff shown against the raw output, derived from the two immutable artefacts, so that I can see exactly what the human changed.
12. As an ops reviewer, I want to accept a decision's output from the foot of the thread, so that consumers may act on it.
13. As an ops reviewer, I want to edit a decision's output by supplying a full replacement, so that the corrected text becomes the actionable artefact while the original stays intact.
14. As an ops reviewer, I want to reject a decision's output, so that nothing downstream can act on it.
15. As an ops reviewer, I want a retried ruling submission to land as the same Entry rather than a duplicate, so that a flaky network can never fork the record.
16. As an ops reviewer, I want the whole Ops surface to speak append-only language — nothing "updates", "edits in place", or "saves" — so that the tool never contradicts the record it fronts.
17. As a demo presenter, I want a Gate panel on the Decision view that asks "what may a consumer act on right now?", so that I can show the gate refusing before a ruling and serving output after one — the platform's core guarantee as a live beat.
18. As a demo presenter, I want the gate's refusal rendered as a calm "no ruling in force" state rather than an error toast, so that the refusal reads as the feature it is.
19. As a demo presenter, I want a Chain page that verifies the MDR chain on demand and shows the verdict, so that I can prove the record intact in front of an audience.
20. As a bank executive watching over a shoulder, I want a chain failure to name where the chain breaks in plain terms, so that tamper-evidence is legible without a cryptography lecture.
21. As a bank executive, I want the Exec surface to lay out new customers, deposits today, cards issued, and fraud prevented as real tiles with an honest "pending" state, so that I see the shape of my dashboard without fabricated numbers in any screenshot.
22. As an ops reviewer with no customer estate, I want logging in to land me on the Ops surface without a crash or an empty banking screen, so that the staff-only role model is provably real.
23. As a customer-only user (a real bank's configuration), I want a Withheld surface to tell me calmly that it isn't part of my access and point me to my surfaces, so that I'm never lost in a mysterious 404.
24. As a customer-only user, I want the admin API to refuse me regardless of what the UI shows, so that frontend role logic is presentation, never security.
25. As a bank evaluator, I want the back office to keep one constant look while the customer app re-skins across brand themes, so that I see that brands are skins and the platform beneath is constant.
26. As an ops reviewer, I want the Ops surface fully operable by keyboard, so that the tool respects back-office working habits.
27. As a compliance officer evaluating the platform, I want every Ops and Exec page to pass the accessibility audit, so that the back office meets the same bar as the customer app.
28. As a demo presenter, I want Sarah's scripted customer journey to work unchanged — same URLs, same behaviour — so that the existing demo script needs no edits.
29. As a signed-out user, I want signing out from any surface to clear everything cached from every surface, so that the next login starts clean.

## Implementation Decisions

- **Step zero: refresh the OpenAPI snapshot and regenerate the client.** The checked-in snapshot lacks the five MDR admin operations and the `roles` field on the user profile; both already exist in the backend's source of truth. Nothing else can start first. The generated client remains the only way the app talks to the API.
- **Route map.** Banking keeps every existing route at `/` unchanged. New: `/ops` (Worklist, index), `/ops/decisions/:decisionId` (Decision view), `/ops/chain` (Chain page), `/exec` (Exec dashboard). The catch-all NotFound stays for unknown URLs. The Access page renders in place at the requested URL for Withheld surfaces — no redirect, no dedicated route.
- **Surface offering** is a pure module: `offeredSurfaces(roles)` and `landingPath(roles)` own the gate map (`CUSTOMER` → Banking; `ADMIN` → Ops + Exec; `SUPPORT_AGENT` → none yet, stated), the landing precedence (`/` → `/ops` → `/exec`), and the no-surface terminal case (Access page). Guards, user-menu switcher rows, and the post-login redirect all consume this one interface. Roles come from the user profile, which resolves them from the JWT — the frontend never derives them elsewhere.
- **Guard split.** The current authenticated guard both proves the session and renders customer chrome. It splits into a session guard (redirect to login when signed out) and a per-surface guard (render the Access page when Withheld). Chrome moves into per-surface shells; the Active-AccountHolder machinery and the holder-scoped header discipline become Banking-shell concerns, so no staff route ever resolves a holder. The CONTEXT.md holder invariant is already rescoped to `CUSTOMER`-role users.
- **Surface switching** lives in each shell's user menu (desktop dropdown; Banking's mobile Profile tab), listing the user's other Offered Surfaces as peers of Sandbox — the ADR-0016 placement rule generalised. No persistent switcher strip; the customer chrome stays customer-shaped.
- **Ops shell**: dense, calm, desktop-first (functional at narrow widths, no bottom-tab chrome), keyboard-first. Nav: Worklist, Chain. Pinned default token set, no theme switcher; Mode (light/dark) shared (ADR-0019). The staff shells scope the theme attribute so the user's persisted brand choice is overridden on staff surfaces and restored on Banking.
- **Worklist**: newest-first (descending chain seq, as served), `rulingState` filter defaulting to awaiting-ruling, Outcome and Ruling in force as glance badges, cost rendered from micros with its currency, latency in ms. Unpaginated, matching the contract.
- **Decision view is a record, not a form.** Read-only sections: rendered prompt, retrieved-context refs with content hashes, raw output, verdict, error detail for failed outcomes (a missing output is a recorded fact, shown as such). The disposition thread renders oldest-first in chain order; an edit ruling shows its derived line-diff. Nothing on the record is editable.
- **Ruling draft**: the append affordance renders at the foot of the thread, where the next Entry will chain. Accept and reject carry the ruling alone; edit requires the full replacement output (never a patch). A dedicated hook owns the draft lifecycle: the Entry id is minted client-side when the draft opens and reused across retries — a replayed append returns the existing Entry, so retries cannot fork the record. On success the thread and worklist refetch; failures surface inline in the draft (mutation marked handled-locally, no generic toast).
- **Gate panel**: on the Decision view, fetches the actionable output on demand. A dedicated hook translates the responses into first-class states: "in force" (which ruling authorised it, plus the output verbatim) and "no ruling in force" (the 409 with the domain error code) — displayed, never toasted. The global query-cache error policy already ignores non-401 query errors, so no policy carve-out is needed for reads.
- **Chain page**: a verify-now action calling the on-demand MDR chain verification; verdict rendered with rows-walked on success, and on failure the failure mode plus the sequence number where the chain breaks, explained in plain language for a watching executive.
- **Exec shell**: real layout, four named Metric tiles (new customers, deposits today, cards issued, fraud prevented), each rendering an honest em-dash with a "metrics API pending" caption. No hardcoded numbers, no skeleton shimmer, no zeroes. Tiles are shaped to bind to generated hooks when the metrics contract lands, without layout rework.
- **Signout** clears the whole query cache — one rule covering every surface's data.
- The audit-plane chain-verification operation (already in the client, unused) stays out of the Ops surface for now; the Chain page proves the MDR scope only, matching the brief.

## Testing Decisions

- A good test asserts external behaviour — what renders, where navigation lands, what the API was asked — never hook internals or component structure.
- **Unit/integration (Vitest, existing patterns):**
  - `surface-offering`: every role combination → offered surfaces + landing path, including the empty and support-agent-only terminal cases. Pure-function tests.
  - The ruling-draft hook: Entry-id stability across retries, edit-requires-full-replacement payload rules, no generic toast on failure, thread invalidation on success. The gate hook: 200 → in-force state, 409 → no-ruling-in-force state, other errors stay errors. Prior art: the existing auth hook tests.
  - Page-level tests for Worklist (filter, badges, ordering), Decision view (record sections, thread, diff, gate panel states, ruling draft), Chain page (verdict states), Access page, Exec shell (honest-pending tiles). Prior art: the existing page tests with in-memory routing.
- **E2E (Playwright, serial, storageState pattern):**
  - Reviewer journey as one spec: worklist → open decision → rule → gate flips → chain verifies. Runs as the shared evaluator user, who holds admin after the backend's demo-policy change.
  - Staff-only journey: the seeded ops reviewer signs in, lands on `/ops`, Banking is Withheld politely, no customer-shaped crash. Needs a second setup project writing a second storage state; credentials via env overrides or a new secret (hand-back below).
  - Surface-gating: a customer-only session sees the Access page on `/ops` and `/exec`, and the admin API's 403s are the proof (the API-failure guard fixture must allow-list those expected statuses). Blocked on the seeded customer-only persona (hand-back below).
  - A11y: every new page (Worklist, Decision view, Chain, Access, Exec) joins the axe spec in both modes.
- The passwordless-flow posture is unchanged: staff personas sign in via the direct Cognito path in test setup, exactly like the existing shared user.

## Out of Scope

- The metrics API and real Exec numbers (later backend increment; fraud-prevented falls out of MDR reject rulings there).
- Any surface for `SUPPORT_AGENT` — explicitly none yet.
- Moving the customer app under `/webapp` (rejected, ADR-0018) and any Banking-surface behaviour change.
- Recording Model Decisions from the UI (the write plane belongs to backend services); the Ops surface only appends Human Dispositions.
- Worklist pagination (contract is deliberately unpaginated at demo scale).
- A dedicated ops token set (revisit per ADR-0019 only if the pinned default proves wrong).
- Mobile-optimised chrome for staff surfaces (desktop-first; functional, not tab-optimised).
- Backend contract changes themselves — gaps are named and handed back, never worked around.

## Further Notes

**Contract and infra hand-backs (named, not worked around):**

1. Type the retrieved-context reference items (type, id, content hash) — currently an untyped object array, forcing the Decision view to render structure the contract doesn't promise.
2. Type or document the verdict object (rendered as formatted JSON until then).
3. Specify what customer-scoped endpoints return for a staff-only token (empty vs 403) — the Access page makes this mostly moot, but the degrade path should be contractual, not accidental.
4. Seed a customer-only user excluded from the registration demo grant — required by the surface-gating e2e; registration now grants every hat, so no such user can be created through the app.
5. An e2e credential path for the seeded ops reviewer (a secret mirroring the existing end-to-end-test-user pattern, or documented env overrides).
6. Webapp housekeeping: CI references a Playwright spec that no longer exists (stale smoke-test filename) — fix independently of this feature.

**First PR** (the smallest that puts a real page in front of a signed-in reviewer): snapshot refresh + regeneration, the surface-offering module, the guard split with Access page and landing precedence, the Ops shell, and the read-only Worklist with the awaiting-ruling default — with its unit, page, and a11y tests. Later increments slice from this PRD via the issue tracker: Decision view (record + thread + diff), Ruling draft + Gate panel, Chain page, Exec shell, staff-persona e2e hardening.

**Demo-continuity constraints carried from the brief:** Sarah's script must not need editing (Banking untouched); the seeded ops reviewer must reach `/ops` without a customer estate; the gate's refusal is a demo beat, not an error.

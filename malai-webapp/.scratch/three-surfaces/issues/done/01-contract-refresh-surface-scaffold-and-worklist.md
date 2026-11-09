# 01 — Contract refresh, surface scaffold, and the read-only Worklist

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md` (grilled decisions in ADR-0018 / ADR-0019; language in CONTEXT.md §Surfaces and Roles, §Ops surface).

## What to build

The first real page in front of a signed-in reviewer, and the frame every later slice drops into.

Refresh the OpenAPI snapshot from the backend's source of truth and regenerate the client — the `roles` field on the user profile and the five MDR admin operations appear; the generated client stays the only way the app talks to the API.

Build the **surface-offering** module: a pure interface answering `offeredSurfaces(roles)` and `landingPath(roles)` — gate map (`CUSTOMER` → Banking, `ADMIN` → Ops + Exec, `SUPPORT_AGENT` → none yet), landing precedence Banking → Ops → Exec, and the no-offered-surface terminal case. Split the authenticated guard's two jobs: a session guard (signed-out → login, unchanged) and a per-surface guard that renders the **Access page** in place — at the requested URL, no redirect — when a Surface is **Withheld**. Chrome leaves the guard: the customer shell and the whole Active-AccountHolder machinery become Banking-shell concerns, so no staff route ever resolves a holder. Post-login **Landing** follows the precedence; for every customer-role user nothing changes.

Build the **Ops shell**: calm, dense, desktop-first, keyboard-first; pinned default token set with no theme switcher, overriding the user's persisted brand choice while on staff surfaces and restoring it on Banking (ADR-0019); Mode shared. Its user menu lists the other Offered Surfaces and sign out. Banking's user menu (desktop dropdown; mobile Profile tab) gains an "Ops" row as a peer of Sandbox for users offered it — the Exec row arrives with slice 06.

The Ops home is the **Worklist**: every Model Decision newest-first as served, `rulingState` filter defaulting to awaiting-ruling, Outcome and Ruling in force as glance badges, model id/version, correlation ref, disposition count, latency, and cost rendered from micros with its currency. Read-only; rows link nowhere yet (slice 02 makes them Decision-view links). Signout clears the entire query cache from any surface.

## Acceptance criteria

- [x] Snapshot refreshed; regenerated client exposes `roles` and the five MDR admin operations; build and existing tests stay green
- [x] surface-offering unit tests cover every role combination, both precedence fall-throughs, and the empty/support-agent-only terminal cases
- [~] A customer+admin user lands on `/` exactly as today; every existing Banking route, deep link, and e2e spec passes unchanged — unit/page suites all green (1043); the live e2e re-run is gated on a running stack
- [x] `/ops` renders the Worklist for an admin user: newest-first, awaiting-ruling default, filter switches between the four ruling states, Outcome and Ruling in force readable at a glance
- [x] A customer-only session at `/ops` gets the Access page in place — calm copy, links to their Offered Surfaces, not the NotFound page, no redirect
- [x] Unauthenticated `/ops` redirects to login like any guarded route — session-guard unit test plus a new case in `redirect-unauthenticated.spec.ts`
- [x] Ops chrome pins the default tokens regardless of the persisted brand theme; Banking restores the choice; Mode applies on both
- [x] Banking's user menu offers Ops (peer of Sandbox) only to users whose roles offer it — desktop dropdown and mobile Profile tab
- [x] Signout from any surface clears all cached data — both shells share `useSignout`, which clears the whole query cache
- [x] Page tests cover Worklist (ordering, filter, badges, formatting) and the Access page; the axe a11y spec covers `/ops` and the Access state in both modes — the Access state runs live by intercepting `GET /api/v1/user` and stripping roles to CUSTOMER, so no second seeded user is needed
- [~] E2E: the shared evaluator user reaches `/ops` from the user menu and sees the seeded mixed-state worklist with the awaiting-ruling default — `ops-worklist.spec.ts` written (menu → worklist → filter flip → back to Banking); the run is gated on a live stack

## What was done (2026-07-18)

All automatable criteria are green: `npm test` 1043 across 149 files, typecheck, eslint, prettier clean; every new Playwright spec resolves under `--list`. The e2e and a11y **runs** (ops-worklist, redirect, the two `/ops` axe describes) need the local malai-api + Postgres + Cognito stack and the seeded mixed-state worklist — run them on the next dev deploy. The Accept-filter step in `ops-worklist.spec.ts` assumes the seed contains at least one accepted decision.

## Blocked by

None - can start immediately.

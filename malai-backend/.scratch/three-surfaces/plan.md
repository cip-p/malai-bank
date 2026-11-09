# Three surfaces — backend enablement

Status: shipped to `main` on 2026-07-18 (issues 01–03 in `issues/done/`)

## Context

The webapp is moving to a three-surface model: `/webapp` (customer app),
`/opsview` (MDR back-office), `/manager-dashboard` (executive numbers), all
reachable by one registered evaluator. The demo policy is "registered
evaluators wear every hat" — but the hats stay real: roles remain enforced at
the API via Cognito groups; the demo environment simply grants them all at
registration. A bank deploying this for real does not enable the policy.

Client brief for the webapp side: `malai-webapp/client-brief.md`.
The MDR feature this exposes: `.scratch/mdr/` (plan, PRD, issues 01–10).

This increment is the backend enablement only — three small issues:

1. `01-roles-on-user-profile.md` — `GET /api/v1/user` gains `roles`
   (contract-first). The session cookies are HttpOnly, so this is the only way
   the frontend can learn which surfaces to offer.
2. `02-registration-demo-all-roles.md` — registration adds `admin` alongside
   `customer`, gated by a demo-policy property.
3. `03-sarah-gets-admin.md` — the seeded Sarah Mitchell joins the `admin`
   group so the scripted demo persona can reach all surfaces.

Deliberately NOT here: a `MANAGER` role and the manager-dashboard metrics API
(later increment, needs its own contract work and a Terraform group in
malai-infra); deleting the seeded ops reviewer (kept — it authors the seeded
MDR dispositions and proves staff-only login works).

Known environment op (not code): the webapp's shared Playwright Cognito user
(`mal-<env>-malai-api-end-to-end-test-user`) predates the registration change
and needs a one-off `admin` group add per environment, or the webapp's ops
e2e journeys will 403.

# MANAGER is a narrow role, separating executive reads from MDR rulings

The Exec surface shipped gated on `ADMIN`, which made the power to read the bank's numbers inseparable from the power to rule on decisions that move money — the first question a bank technologist asks about a back office. We decided to add a fourth role, `MANAGER`, which Offers the Exec surface and nothing else; `ADMIN` remains the superset and keeps Exec, so the metrics endpoints gate on `hasAnyRole('MANAGER','ADMIN')`. The cost is real — a Cognito group, a widened `roles` response enum that the compatibility check flags, and a seeded manager-only persona plus a gating e2e without which the role would be observationally identical to `ADMIN` for a demo evaluator holding every hat.

## Considered Options

- **Keep `ADMIN` gating Exec** — free, and the demo evaluator cannot tell the difference since the demo policy grants every hat anyway. Rejected: separation of duties is exactly what this audience probes, and re-gating later would mean changing an authorization rule *and* widening a published enum after the fact.
- **Add `MANAGER` without a proof persona** — cheaper by a seed persona and a Playwright storage state. Rejected: a fourth role in the contract whose boundary is never demonstrated reads as aspirational, which is worse than the honest three-role model. The narrow role is only real if something proves it refuses.
- **Reuse `SUPPORT_AGENT`** — rejected: it already means read-only across customer accounts, an unrelated hat, and overloading it would corrupt a term the glossary defines.

## Consequences

Cognito group precedence is renumbered, which is cosmetic — the backend resolves roles from `cognito:groups`, never `cognito:preferred_role`. `surfaceOffering.ts` changes shape: its `GATES` map goes from surface → one Role to surface → set of Roles, because Exec now has two. A manager-only persona has no customer estate, so it inherits the 403/`4999` degrade path that three-surfaces issue 06 already shipped — no new contract work for the customer-scoped endpoints.

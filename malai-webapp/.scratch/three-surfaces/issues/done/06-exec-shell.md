# 06 — Exec shell: the designed dashboard with honest tiles

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

The Exec surface as a designed shell. The route exists behind the surface guard (admin-gated, Access page when Withheld), the layout and navigation are real, and the four Metric tiles — new customers, deposits today, cards issued, fraud prevented — are laid out for real but render an honest em-dash with a quiet "metrics API pending" caption. No hardcoded values, no zeroes, no skeleton shimmer: nothing that could read as a real number in a screenshot. Tiles are shaped so the generated hooks bind in without layout rework when the metrics contract lands. The shell may be more generous than the Ops tone — it exists to impress — but no charts for the sake of charts. Staff theming rules apply (pinned default tokens, Mode shared); user menus across surfaces gain the Exec row for users offered it.

## Acceptance criteria

- [ ] `/exec` renders the dashboard shell for an admin user; a customer-only session gets the Access page; unauthenticated redirects to login
- [ ] The four named Metric tiles render the honest pending state; page tests assert no numeral appears anywhere in a tile
- [ ] Banking and Ops user menus offer Exec to users whose roles offer it; the Exec menu offers the way back
- [ ] Pinned default tokens and shared Mode, matching the Ops shell
- [ ] Axe a11y spec covers the page in both modes
- [ ] E2E: the evaluator reaches `/exec` from the user menu and sees the pending tiles

## Blocked by

- 01-contract-refresh-surface-scaffold-and-worklist

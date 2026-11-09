# 08 — Accessibility spec and Lighthouse ship gates

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The final quality gates that make the home page shippable. Lands once the page is structurally complete (hero, all three sections, close-out, footer) so the gates are run against the real surface, not a half-built one.

Two pieces:

**1. An accessibility Playwright spec** using `@axe-core/playwright`. Loads the home page, runs an axe scan, asserts zero violations. Runs in CI on every change. This catches a11y regressions locally before Lighthouse runs.

The spec must cover the home page at desktop and mobile viewports (the latter matters because the visual register's responsive behaviour can introduce contrast or hit-target regressions that desktop misses). The exact viewports should match those already configured in `playwright.config.ts`; if none are configured, use a standard pair (e.g. 1440 × 900 desktop, 390 × 844 mobile).

`@axe-core/playwright` is added as a dev dependency on this slice.

**2. Lighthouse CI ship gates.** `@lhci/cli` and `lighthouserc.json` already exist in the repo. Tighten the thresholds so the build fails if:

- Performance < 95
- Accessibility < 100
- Best practices < 95
- SEO < 95

(Performance and accessibility are the non-negotiable gates per the PRD; best-practices and SEO are added at slightly lower thresholds because they cover concerns we should not regress on either.)

Run Lighthouse against the production-built home page (`astro build` then `astro preview`), not the dev server. This must be wired into `ci.sh` to run on every CI invocation.

Document the gates briefly in the README or CONTEXT.md so contributors know what blocks merge.

## Acceptance criteria

- [ ] `@axe-core/playwright` is added as a dev dependency.
- [ ] An accessibility Playwright spec exists and asserts zero axe violations on the home page at both a desktop and a mobile viewport.
- [ ] The a11y spec runs in CI and fails the build on any violation.
- [ ] `lighthouserc.json` enforces Performance ≥ 95, Accessibility = 100, Best practices ≥ 95, SEO ≥ 95.
- [ ] Lighthouse CI runs against the production-built page (`astro build` + `astro preview`) in CI.
- [ ] All gates pass on the current state of the home page (i.e. the page is genuinely compliant, not the thresholds loosened to fit).
- [ ] The README or `CONTEXT.md` records the ship gates so future contributors discover them without reading CI scripts.

## Blocked by

- `04-functionality-section.md`
- `05-security-section.md`
- `06-architecture-section.md`
- `07-close-out-and-footer.md`

(The gates are only meaningful once the full page is in place; running them against a partial page produces noise, not signal.)

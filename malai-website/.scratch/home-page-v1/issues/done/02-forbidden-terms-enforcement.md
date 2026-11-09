# 02 — Forbidden-terms enforcement

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The constraint enforcement layer that protects the [[Reference build]]'s public surface from leaking cloud provider names or internal service identifiers — both at source authoring time and post-render. This slice is what stops every later slice from quietly eroding ADR-0001's regulatory-distance posture.

Three things land together:

- **`docs/forbidden-terms.json`** — the canonical list, currently:

  > `AWS`, `Amazon`, `ECS`, `Fargate`, `RDS`, `S3`, `CloudFront`, `MalaiBank`, `malai-api`, `malai-webapp`, `account-service`, `account-holder-service`, `payee-service`, `direct-debit-service`, `standing-order-service`, `profile-service`, `auth-service`.

  The list is a living artefact — when new internal names appear elsewhere in the Malai workspace, they are appended here.

- **A pure check function `findForbidden(text): string[]`** — single source of truth used by both the lint script and the Playwright spec. Case-sensitive by default. Returns the list of forbidden strings found in the input.

- **Three consumers wired into CI**:
  1. A **Vitest** unit test for `findForbidden()` asserting known-forbidden strings are flagged, known-clean strings pass, near-misses (e.g. _BACS_ — not forbidden) are not flagged, and empty input returns `[]`. Vitest is added as a dev dependency on this slice; the website's posture mirrors the [[Webapp]]'s existing setup.
  2. A **CI lint script** (Node) that imports `findForbidden()` and runs it against every `src/**/*.astro` and every content source file. Exits non-zero on any match. Wired into `ci.sh` so a PR introducing a forbidden term fails before review.
  3. A **Playwright spec** that crawls every page in the built sitemap and asserts `findForbidden(html)` returns `[]` for each. Runs in the same CI step as the smoke spec from Issue 01.

The check is over rendered HTML, so build-time substitutions, third-party embeds, or future copy-from-content drift are all caught.

## Acceptance criteria

- [ ] `docs/forbidden-terms.json` exists with the canonical list and is documented as the place to append new internal names.
- [ ] `findForbidden(text): string[]` is exported from a single module; both the lint script and the Playwright spec consume it.
- [ ] Vitest is added as a dev dependency; the unit test for `findForbidden()` covers positive, negative, near-miss, and empty cases and passes.
- [ ] The lint script is invocable from `ci.sh`; running it against a clean tree passes, running it against a tree that contains _"AWS"_ in a `.astro` file fails non-zero.
- [ ] The Playwright crawl spec drives off the sitemap, exercises every emitted page, and asserts no forbidden term appears in the rendered HTML of any page.
- [ ] `ci.sh` runs both the lint script and the Playwright crawl on every CI run.
- [ ] The README or `CONTEXT.md` is updated with a one-line pointer to `docs/forbidden-terms.json` as the place to add new internal names.

## Blocked by

- `01-walking-skeleton.md` — the Playwright crawl needs a built page to scan.

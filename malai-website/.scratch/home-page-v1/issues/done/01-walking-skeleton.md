# 01 — Walking skeleton: Layout, hero, page-smoke spec

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

A deployable home page consisting only of the layout, the hero, and a working Playwright smoke test. This is the tracer bullet that proves the build pipeline end-to-end before any section work begins.

The page sets up the foundations every later slice consumes:

- A `Layout.astro` that wraps `astro-seo`, sets the canonical title (_Malai — a modern UK retail bank, end to end._), description (echoing the hero subhead), and a single OG image (a typographic placeholder card aligned with the hero copy). Wires `@astrojs/sitemap`.
- A `content` module with typed shapes for hero copy. Marketing strings live here; the section is read for verbatim assertions. The hero is the first occupant; later slices grow this collection with their own typed entries.
- A Tailwind configuration tuned to the builder-confident neutral register per ADR-0002 — a neutral grey scale, a single accent colour, one geometric-sans type ramp, a generous spacing scale.
- `src/pages/index.astro` composing the layout + hero only.
- A `Hero` component that consumes `content` and renders the canonical strings:
  > **A modern UK retail bank, end to end.**
  >
  > Malai is a working reference platform — not an authorised bank, holds no money, but the architecture, security and customer flows are real. Open an account in the demo, then read how it's built.
  >
  > [Try the demo →] (links to the [[Webapp]] signup at `https://app.malaibank.com` — verify the exact signup deep link against the running webapp) [Talk to us] (`mailto:info@malaibank.com`)
- A Playwright smoke spec that loads the page, asserts the hero copy is present verbatim (read from the same `content` module the page uses), and asserts both CTAs have the expected `href`.

The page must render with zero client-side JavaScript at this stage.

The brand on the page is _Malai_, never _MalaiBank_, per ADR-0001. The string _MalaiBank_ must not appear in rendered HTML on this slice.

## Acceptance criteria

- [ ] `Layout.astro` exists and is consumed by the home page; `astro-seo` and `@astrojs/sitemap` are wired and produce a sitemap on build.
- [ ] A `content` module exists with a typed schema for the hero (eyebrow, headline, subhead, primary CTA label + href, secondary CTA label + href).
- [ ] Tailwind config carries tokens matching the visual register described in ADR-0002.
- [ ] `src/pages/index.astro` renders the layout and the `Hero` component only.
- [ ] Hero copy on the rendered page matches the canonical strings verbatim.
- [ ] Primary CTA links to the [[Webapp]] signup deep link; secondary CTA is a `mailto:info@malaibank.com`.
- [ ] Rendered HTML contains no instance of _MalaiBank_.
- [ ] A Playwright smoke spec passes locally and in CI; it imports the same hero strings the page does to keep assertions self-consistent.
- [ ] No client-side JS ships from this slice (no React islands, no `client:*` directives).
- [ ] `ci.sh` runs the smoke spec on every CI run.

## Blocked by

None — can start immediately.

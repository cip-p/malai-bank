# 03 — Sticky navigation: anchor links + scroll-aware island

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The single interactive surface on the home page — a slim navigation bar that gives the [[Visitor]] persistent access to the demo CTA and the three section anchors at any scroll position. This is the only piece of the page that ships React; everything else stays zero-JS.

The component is a `StickyNav` Astro island (React, hydrated `client:load`). Its contents:

- Anchor links: _Functionality_, _Security_, _Architecture_, _Talk to us_.
- A primary **Try the demo →** button on the right that points at the same [[Webapp]] signup URL the hero uses.
- The brand wordmark _Malai_ on the left.

The behaviour:

- First render: the nav lives inside the hero (statically positioned).
- After the hero scrolls out of view: the nav becomes a fixed bar at the top of the viewport.
- While fixed: scrolling down translates the nav out of view; scrolling up brings it back. Direction is the only trigger — not absolute scroll position, not velocity.
- The transition is brief (≤ 200ms) and respects `prefers-reduced-motion`.

Anchor IDs (`#functionality`, `#security`, `#architecture`) may not yet correspond to real sections at the moment this slice ships — that is fine. The section slices (04, 05, 06) attach those IDs when they land. A click on an anchor that does not yet have a target is a no-op, not an error.

A Playwright e2e spec exercises the behaviour: page loads with the nav inside the hero; scrolling past the hero pins it to the top; further downward scroll hides it; upward scroll reveals it; the anchor links carry the correct `href` values; the primary CTA matches the hero's primary CTA URL exactly.

The brand text in the nav is _Malai_. The string _MalaiBank_ must not appear in the rendered HTML.

## Acceptance criteria

- [ ] A `StickyNav` React component exists and is the only `client:*`-hydrated island on the page.
- [ ] The nav renders inside the hero on initial load and pins to the top after the hero scrolls out of view.
- [ ] Scroll-down hides the nav; scroll-up reveals it; transitions respect `prefers-reduced-motion`.
- [ ] Anchor links reference `#functionality`, `#security`, `#architecture`, and a _Talk to us_ `mailto:`.
- [ ] The nav's primary CTA `href` matches the hero's primary CTA `href` (both consumed from the same `content` source).
- [ ] A Playwright e2e spec asserts: initial in-hero placement, post-hero pinning, hide on scroll-down, reveal on scroll-up, anchor `href` values, primary-CTA `href` consistency with the hero.
- [ ] The page-smoke spec from Issue 01 still passes — adding `StickyNav` has not regressed hero assertions.
- [ ] No forbidden term from Issue 02 appears in the nav or its rendered output.

## Blocked by

- `01-walking-skeleton.md` — needs the page, layout, and content infrastructure.

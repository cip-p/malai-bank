# 07 — Close-out section and footer

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The two final structural surfaces of the home page: a close-out CTA section directly above the footer (so a [[Visitor]] who scrolled all the way through has the same two calls to action they saw in the hero), and the footer itself.

**Close-out section** sits below the Architecture section and consists of:

- A short closing line in the same builder-confident neutral voice — one sentence, no exclamation.
- The two CTAs again: **Try the demo →** (same [[Webapp]] signup deep link as the hero and `StickyNav`) and **Talk to us** (`mailto:info@malaibank.com`).

**Footer** sits below the close-out and has three columns:

1. The _Try the demo_ / _Talk to us_ CTAs repeated.
2. The regulatory disclosure one-liner verbatim: _"Malai is a demonstration platform. Not a UK-authorised bank. No funds held."_
3. `info@malaibank.com` plus the copyright year.

Specifically **not** in the footer: sitemap repeat, social links, navigation repeat. The sticky nav from Issue 03 handles in-page navigation; the footer's job is just to repeat the disclosure and the contact.

The brand on these surfaces is _Malai_. All CTAs must consume their labels and `href` values from the same `content` module the hero and nav use, so a change to the signup URL takes effect in one place. The copyright year is computed at build time (not hard-coded as a literal that goes stale).

The page-smoke spec extends to assert: the close-out section is present with the closing line and both CTAs (matching the hero's `href` values), the footer disclosure string is present verbatim, the footer contact email is `info@malaibank.com`, the copyright year is the current build year.

No client-side JS is added by this slice. Forbidden terms must not appear anywhere in these surfaces.

## Acceptance criteria

- [ ] A `CloseOut` Astro component and a `Footer` Astro component exist and are mounted in `index.astro` in that order, after the Architecture section.
- [ ] Both `CloseOut` CTAs consume from the same `content` entries the hero and `StickyNav` consume; their `href` values match the hero's exactly.
- [ ] The footer renders three columns with the contents specified above and no others.
- [ ] The footer disclosure string is present verbatim.
- [ ] The footer copyright year is computed at build time, not hard-coded.
- [ ] Page-smoke spec extends to cover close-out section presence and content, footer disclosure string, footer email, computed copyright year.
- [ ] No client-side JS hydrated by these components.
- [ ] No forbidden term appears in rendered HTML for these components.

## Blocked by

- `01-walking-skeleton.md` — needs the layout, content module, and page composition. May land before or after Issues 04–06.

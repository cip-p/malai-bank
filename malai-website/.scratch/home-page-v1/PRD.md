# PRD — Home page v1

Status: ready-for-agent

## Problem Statement

[[Malai]] has a running banking [[Webapp]] and a credible backend, but no public surface a [[Visitor]] can land on. A bank executive or technologist who hears about Malai today has nowhere to evaluate it from. Without a marketing site, there is no funnel — they cannot understand what the [[Reference build]] is, cannot try the demo without out-of-band credentials, cannot read about the architecture or security posture, and cannot start a conversation with the team. The risk is that a credible engineering artefact sits invisible while prospective buyers move on.

Additionally, using the word _Bank_ commercially in the UK is regulated under FSMA and the team is explicitly not authorised — any public surface must be designed with that constraint embedded, not bolted on.

## Solution

A single-page marketing site that introduces [[Malai]] as a reference UK retail bank, demonstrates it through a working demo, and explains the architecture, security and functionality at a depth that a CTO would respect. The site is honest about not being an authorised bank from the hero downward, uses _Malai_ as the public brand (per ADR-0001), and adopts a builder-confident neutral visual register (per ADR-0002) that reinforces _"these people build serious software."_

From the [[Visitor]]'s perspective: they land on `malaibank.com`, get the proposition in five seconds, click _Try the demo_ into the [[Webapp]] signup, or scroll down a confident three-act story — _what it does → why it's safe → how it's built_ — then either book a demo conversation via email or come back to the site after using the demo. The visitor never wonders if Malai is a real bank; the disclosure is part of the pitch.

## User Stories

### Bank executive — the exec [[Visitor]]

1. As a bank executive, I want to understand what Malai is within five seconds of arriving, so that I can decide whether to keep reading.
2. As a bank executive, I want to know immediately that this is a demonstration platform and not an authorised bank, so that I do not later feel misled.
3. As a bank executive, I want to try the demo with one click, so that I can form a real first impression without depending on a sales call.
4. As a bank executive, I want to see what the bank can actually do (payments, direct debits, CoP, ledger), so that I can judge whether the feature set is credible.
5. As a bank executive, I want to see a "follow the money" walkthrough showing one user journey end-to-end, so that I can picture how a real customer would experience it.
6. As a bank executive, I want a clear way to start a conversation with the team, so that I can engage if I'm interested.
7. As a bank executive scrolling mid-page, I want the _Try the demo_ call to action to remain reachable, so that I do not have to scroll back to act.
8. As a bank executive, I want the site to load quickly and feel polished, so that my first impression of the platform's quality is positive.

### Bank technologist — the CTO/architect [[Visitor]]

9. As a bank technologist, I want to scroll past the exec-friendly hero into substantive technical content, so that I can evaluate whether the architecture is sound.
10. As a bank technologist, I want a single architecture diagram showing the system shape, so that I can judge the design in one glance.
11. As a bank technologist, I want to see the architecture diagram in abstracted terms (no specific cloud provider, no internal service names), so that I am evaluating the pattern, not the vendor.
12. As a bank technologist, I want load-bearing patterns named explicitly (cloud-native, event-driven, idempotency on sync and async paths, three-pillar observability, IaC, CI/CD with smoke tests), so that I can quickly map what is and isn't being claimed.
13. As a bank technologist, I want to read a public `openapi.yaml` describing the actual API, so that I can verify the surface area matches the marketing claims.
14. As a bank technologist, I want a scannable security trust ledger (TLS, JWTs signed by managed key, hash-chained audit, RBAC, OAuth2 inter-service, secrets in managed store), so that I can tick off the table-stakes patterns without hunting.
15. As a bank technologist, I want each security claim to sit beside its evidence, so that I am not asked to take any one on faith.
16. As a bank technologist, I want to see (eventually) published [[Kensa]] BDD scenario reports, so that I can see real cross-system behaviour rather than promises.
17. As a bank technologist, I want links into the running demo from individual claims, so that I can verify by interacting rather than reading.

### Both audiences — site behaviour

18. As any visitor, I want the page to be navigable by section anchors, so that I can jump straight to what interests me.
19. As any visitor, I want the navigation to stay accessible while reading, so that I do not lose orientation on a long page.
20. As any visitor, I want the page to be fully accessible (zero axe violations, Lighthouse a11y 100), so that the site does not exclude readers with assistive technologies.
21. As any visitor reading on mobile, I want every section to remain legible and the demo CTA to remain clickable, so that the experience does not degrade.
22. As any visitor, I want to find the disclosure repeated in the footer, so that the regulatory framing is unambiguous wherever I am on the page.

### Internal — the Malai team

23. As a member of the Malai team, I want all copy to live in typed content collections and not in component markup, so that changing a card headline does not require touching a component.
24. As a member of the Malai team, I want a CI lint that fails when any source file contains a forbidden term (cloud provider names, internal service names), so that the regulatory-distance posture is not eroded by drift.
25. As a member of the Malai team, I want a Playwright spec that crawls the rendered site and fails on the same forbidden terms, so that build-time substitutions or third-party content cannot leak them post-render.
26. As a member of the Malai team, I want to add to the forbidden-term list in one place when a new internal name appears anywhere in the workspace, so that enforcement keeps up with the codebase.
27. As a member of the Malai team, I want Lighthouse performance ≥ 95 and accessibility = 100 as CI ship gates, so that regressions block merge rather than ship.
28. As a member of the Malai team, I want the public brand on the marketing surface to be _Malai_ (not _MalaiBank_), so that the regulatory posture from ADR-0001 is preserved.

## Implementation Decisions

### Architecture & shape

- **Single home page** on Astro 5. No multi-page navigation, no `/about`, no `/security` page. Sections live within `src/pages/index.astro`.
- **Section order** below the hero: Functionality → Security → Architecture, ending with a close-out CTA section and a footer. Order chosen to suit the exec's reading arc (per CONTEXT.md _Site shape_).
- **Astro islands** are used only where genuinely interactive. The sticky navigation is the one confirmed island. The rest of the page ships zero client-side JS unless a later evidence decision (e.g. interactive architecture diagram) requires it.
- **Visual register** is builder-confident neutral per ADR-0002 — monochrome base, single accent, geometric sans, generous whitespace. Tailwind config carries the tokens; component shapes lean shadcn-compatible so the website and the [[Webapp]] feel like siblings.
- **Public brand** is _Malai_ throughout per ADR-0001. The string _MalaiBank_ appears nowhere in rendered marketing copy. The `malaibank.com` domain and `info@malaibank.com` contact email are accepted minor inconsistencies recorded in ADR-0001 and are not in scope to change.

### Modules to build

- **`content` (deep).** Typed content collections under `src/content/` (or equivalent typed data files). Holds the verbatim hero strings, the eight capability cards, the trust-ledger claims, the "follow the money" walkthrough steps, the architecture annotation strings, and the footer copy. Components consume this layer; no marketing string is inlined into a component. The interface is _typed data shapes_; the schema is the only thing that needs to be stable.
- **`forbidden-terms` (deep).** A data file (`docs/forbidden-terms.json`) listing forbidden strings — _AWS_, _Amazon_, _ECS_, _Fargate_, _RDS_, _S3_, _CloudFront_, _malai-api_, _malai-webapp_, _account-service_, _account-holder-service_, _payee-service_, _direct-debit-service_, _standing-order-service_, _profile-service_, _auth-service_ — plus a small check function `findForbidden(text): string[]`. The function is the single point of truth used by both the CI lint script and the Playwright spec.
- **`StickyNav` (deep, React island).** A single React component that renders anchor links for the three sections plus _Talk to us_, with **Try the demo** as a primary button on the right. Lives inside the hero on first render. Once the hero scrolls out of view it sticks to the top of the viewport; hides on downward scroll, reveals on upward scroll. The component owns its scroll listener and visibility state machine and is the only interactive surface on the page.
- **`Layout.astro` (shallow).** Wraps `astro-seo`, sets a single OG image, title (_Malai — a modern UK retail bank, end to end._) and description echoing the hero subhead. Hosts `@astrojs/sitemap` configuration.
- **Section components (shallow).** `Hero`, `FunctionalitySection`, `SecuritySection`, `ArchitectureSection`, `CloseOut`, `Footer` — each consumes from `content` and renders markup. No business logic.
- **`src/pages/index.astro` (shallow).** Composes the sections in order.
- **Tailwind config.** Tokens reflecting the builder-confident neutral register: a neutral grey scale, one accent colour, a single type ramp using a geometric sans, generous spacing scale.
- **CI lint script.** A small Node script that imports `findForbidden()` and runs it against every `src/**/*.astro` and content source file. Exits non-zero on a match. Wired into `ci.sh`.

### Hero copy (canonical, verbatim — lives in `content`)

> **A modern UK retail bank, end to end.**
>
> Malai is a working reference platform — not an authorised bank, holds no money, but the architecture, security and customer flows are real. Open an account in the demo, then read how it's built.
>
> [Try the demo →] [Talk to us]

The primary CTA links to the [[Webapp]] signup. The secondary CTA is a `mailto:info@malaibank.com`. These two CTAs are repeated in the close-out section above the footer.

### Section shapes

- **Functionality.** A short narrative paragraph framing _what this bank does_, then a 6–8-card capability grid. Each card carries a one-line claim and a one-line proof that links into the running [[Webapp]] where possible. Embedded inside the section is a short _follow the money_ mini-walkthrough — open account → fund → FPS payment → CoP check → audit entry. Initial capability set: real-time payments (FPS), direct debits & standing orders, confirmation of payee, double-entry ledger, multi-rail settlement (BACS, SWIFT), themeable mobile-first app, modular feature additions, auditable money movement.
- **Security.** A vertical _trust ledger_ — a list of standards-language claims, each with its evidence beside it: TLS at the edge, JWTs signed with a managed key and validated per request, append-only hash-chained audit, secrets in a managed store under per-task identity, RBAC at the API, OAuth2 between services. Format mirrors how CISOs scan vendor security pages.
- **Architecture.** Diagram-first — a single abstracted architecture diagram is the section's centrepiece. Component names abstract per ADR-0001 and the brief (no cloud provider names, no internal service names). Annotations surface the load-bearing patterns: cloud-native, event-driven, idempotency on sync and async paths, three-pillar observability, IaC for per-environment standup, CI/CD with smoke tests. A link to the public `openapi.yaml` lives in this section.

### Disclosure

The _"not a real bank"_ disclosure is part of the hero subhead, not a footnote. The footer repeats it as a one-liner: _"Malai is a demonstration platform. Not a UK-authorised bank. No funds held."_ No persistent top banner is added by default. A persistent banner may be introduced later if counsel requires it.

### Navigation behaviour

- The sticky nav lists three anchors: Functionality, Security, Architecture, plus _Talk to us_. The right-hand side carries the **Try the demo** primary button at all times.
- Initial render places the nav inside the hero.
- After the hero scrolls out of view, the nav adopts a fixed position at the top.
- Scrolling down hides the nav (translateY out of view); scrolling up reveals it.

### Footer

Three columns:

1. The _Try the demo_ / _Talk to us_ CTAs as a close-out repeat.
2. The regulatory disclosure one-liner.
3. `info@malaibank.com` and the copyright year.

No sitemap repeat, no social links, no nav repeat.

### SEO, performance, accessibility

- `astro-seo` owns meta; `@astrojs/sitemap` owns the sitemap. Single OG image (architecture diagram or typographic card aligned with the hero).
- Lighthouse Performance ≥ 95 and Accessibility = 100 are ship gates. `@lhci/cli` is already wired.
- Images served as AVIF with WebP fallback.
- Astro islands are scoped tightly. The hero, sections, and footer ship zero client JS. Only `StickyNav` is hydrated.

### Language

UK English throughout. _Cheque_, _programme_, _organisation_, _centre_, _colour_. Money in GBP. Dates `DD MMM YYYY`. No dictionary lint added in v1; copy review catches drift.

### Content constraints

The forbidden-terms list at `docs/forbidden-terms.json` is enforced by both the lint script and a Playwright spec crawling the rendered site (driven by the sitemap). When new internal names appear anywhere in the Malai workspace, they are added to this list.

## Testing Decisions

### What makes a good test

A good test on this codebase asserts **external behaviour** — what a reader, a script, or a `curl` would observe — not internal implementation. Specifically:

- A good test reads like a user story or a constraint, not like a component's prop list.
- A good test should still pass after the implementation is rewritten, provided the observable behaviour is the same.
- Snapshot tests that capture rendered markup wholesale are avoided — they fail noisily on cosmetic changes and add little signal.
- The test surface is kept small and high-value: one Playwright spec per real user-facing behaviour, one Vitest for the only piece of pure logic on the site.

### Modules under test

- **`forbidden-terms`.** Vitest unit test for `findForbidden(text)`. Asserts that all known-forbidden strings are flagged when present, that whitelisted-but-similar strings (e.g. _"BACS"_ — not forbidden) are not flagged, and that an empty / clean input returns an empty array. Adding Vitest as a dev dependency to the website matches the webapp's posture (which already uses it).
- **`StickyNav`.** Playwright e2e. Scroll the page; assert that the nav is hidden initially after the hero leaves, that scrolling up reveals it, that scrolling down hides it, that each anchor link navigates to a real `id` on the page, that the demo CTA inside the nav points at the [[Webapp]] signup URL.
- **Page smoke.** Playwright e2e. The home page renders; the hero copy matches the canonical strings verbatim (loaded from the same `content` module the page uses); both hero CTAs have the correct `href`; the close-out CTAs match; the footer disclosure string is present.
- **Forbidden-terms (rendered).** Playwright e2e. Crawl every page in the sitemap; for each, `findForbidden(html)` returns `[]`. This protects against build-time substitutions or third-party embeds leaking forbidden terms.
- **Accessibility.** Playwright e2e using `@axe-core/playwright`. Zero violations on the home page. Catches a11y regressions locally before Lighthouse runs in CI.

### Modules not tested

- Section components (`Hero`, `FunctionalitySection`, `SecuritySection`, `ArchitectureSection`, `CloseOut`, `Footer`) are not tested individually. They are markup glue between `content` and the DOM; the page-smoke Playwright spec already exercises every observable string they render.
- `content` is not runtime-tested. TypeScript catches shape drift; verbatim assertions live in the page-smoke spec.
- `Layout.astro` and Tailwind config are not tested. Lighthouse and the a11y spec cover their effects.

### Prior art

- The webapp (`malai-webapp-v1`) already uses Vitest for unit tests and Playwright for E2E plus a dedicated a11y spec. The test posture here mirrors that — Vitest only for pure logic; Playwright for behaviour; an a11y spec as a peer to E2E.
- `playwright.config.ts` exists in this repo already; new specs slot in next to whatever stubs are there.
- The pre-existing `lighthouserc.json` and `@lhci/cli` covers the perf and a11y CI gates and does not require changes from this PRD.

## Out of Scope

- **Evidence and screenshot inventory.** Which specific [[Webapp]] screenshots are embedded where, which [[Kensa]] scenarios are surfaced and in what format, whether the architecture diagram is interactive or static, whether the openapi link points to Swagger UI or a raw file — all deferred to a follow-up grilling session. v1 ships with placeholder evidence slots in the content schema so copy can be added without touching components later.
- **Backend changes.** The [[Webapp]] signup flow is consumed as-is. No new auth machinery, no ephemeral sandbox endpoint. A separate follow-up is needed for the demo backend to publicly serve `openapi.yaml` before the Architecture section's link goes live — that follow-up is not part of this PRD.
- **Webapp changes.** No edits to `malai-webapp-v1` are required to ship this PRD.
- **Domain change.** Acquiring a `malai.*` domain is not in scope — the `malaibank.com` domain stays. ADR-0001 records the residual exposure.
- **Pages other than the home page.** No `/about`, `/security`, `/pricing`, blog, press, team, or case-studies pages. Listed explicitly in CONTEXT.md _Out of scope for v1_.
- **Cookie banner.** No analytics tracking ships in v1, therefore no banner. Plausible or similar privacy-respecting analytics is the most we'd add later.
- **Persistent top regulatory banner.** Not added by default. Reserved for a future change if counsel requires it.
- **UK-English dictionary lint.** Not wired in v1; copy review catches drift.

## Further Notes

- This PRD inherits from `CONTEXT.md` at the repo root and the two ADRs (`docs/adr/0001-public-brand-is-malai-not-malaibank.md`, `docs/adr/0002-visual-register-is-builder-confident-neutral.md`). Implementers should read both before starting.
- Two dependencies sit outside this PRD's scope and will gate parts of the implementation: (1) the demo backend publicly serving `openapi.yaml`, and (2) the deferred evidence/screenshot session that decides what fills the section content slots beyond text. Implementation can proceed up to the point where either is required; placeholders in `content` are acceptable until then.
- The forbidden-term list is a living artefact. When a new internal name appears anywhere in the Malai workspace (e.g. a new backend service), an issue should be opened against this repo to append it to `docs/forbidden-terms.json`.
- The grilling session that produced this PRD covered fourteen design decisions; the resolutions are captured in `CONTEXT.md` with ADRs for the two hardest-to-reverse calls (brand, visual register). The remaining twelve decisions live as glossary entries and section descriptions in `CONTEXT.md` — they are not ADR-worthy individually but are referenced from this PRD.

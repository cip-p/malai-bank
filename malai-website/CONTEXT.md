# Context — malai-website

The marketing site for [[Malai]]. Glossary only — no implementation details.

## Terms

### Malai

A worked example of a modern UK retail bank. Not a real bank; holds no money. Exists so a [[Visitor]] can evaluate the underlying platform and engage the builders to adapt or recreate it.

The public-facing brand on the website is **Malai**, not **MalaiBank**. Using "Bank" commercially in the UK triggers FSMA / PRA / FCA scrutiny, and we are explicitly not authorised. "MalaiBank" appears only inside the [[Webapp]] sandbox, where the visitor is clearly in a demo context.

### Reference build

The thing being sold on this site. Not a packaged product to license off-the-shelf, and not a portfolio piece. The visitor's takeaway should be _"this is a credible blueprint for a UK retail bank, and I'd want to engage the people who built it to do one for us."_

The deliverables behind the offer are the architecture, code patterns, security posture, and the running demo — not a shrink-wrapped platform with SLAs.

### Visitor

The reader the site is written for. A bank executive, a bank technologist, or anyone evaluating whether to invest in a banking solution. Specifically _not_ a retail banking customer — the customer-facing app lives elsewhere ([[Webapp]]).

### Webapp

The demo app at `https://app.malaibank.com`. The website links to it; together they form the demo experience a [[Visitor]] explores. One sign-in fronts all three [[Surfaces]]; an evaluator who registers is granted every role.

### Surfaces

The product's three sign-in experiences on one platform: **Banking** (the customer app), **Ops** (the back-office worklist over the [[Model Decision Record]]) and **Exec** (the executive dashboard). Role separation between them is enforced at the API, not just in the interface. The website mirrors them: Banking is the home page's functionality section; Ops and Exec each carry a standalone page (`/operations`, `/executive`).

### Model Decision Record

The platform's append-only, hash-chained record of AI-assisted decisions — each model invocation (model and prompt-template versions, rendered prompt, raw output, verdict, latency, cost) and every human ruling appended to it. Provider-agnostic: a deterministic rules engine and a frontier model land on the same record. Not the security industry's "managed detection and response". Presented on `/operations`.

### Copilot

A read-only investigation aid on the Ops surface: it explains a screened payment as ordered claims, each citing a source record the bank holds. Grounding is machine-checked and all-or-nothing, every invocation is itself recorded to the [[Model Decision Record]] as advisory, and no service can consume its output — the site's copy must never present it as a decision-maker.

## Site shape

The website is a home page plus deep pages. The hero is written for the executive end of the [[Visitor]] spectrum — confident pitch, working demo CTA. The body of the page progressively reveals depth for the technical end. We still do not split routes by _audience_ — every page addresses the [[Visitor]] — but we do split by _product surface_: each staff surface carries a standalone page (`/operations`, `/executive`) because each carries its own evidence (ADR-0004).

Below the hero, four sections in this order: **Surfaces → Functionality → Security → Architecture**. The arc is _what am I looking at → what does it do → is it safe → is the build sound_. The exec gets value in the first two sections (and the demo link lives there); the technologist stays to the end. Security and Architecture render as teasers linking to their standalone pages.

The "not a real bank" disclosure is part of the hero pitch, not a footnote — framed as honesty rather than fine print. A one-line footer disclosure exists for completeness. We may add a thin persistent top banner later if counsel requires it; we do not by default.

The hero CTA links into the [[Webapp]] signup flow. The visitor self-registers a sandbox account. We accept the signup friction in exchange for honesty (this is the real product) and to avoid building new auth machinery in the backend purely for the website's sake.

A secondary CTA — **"Talk to us"** — opens a `mailto:info@malaibank.com`. The site's voice uses "we" / "the Malai team"; no individual is named. The same two CTAs repeat in a close-out section near the page footer for visitors who scroll past the hero before deciding.

## Visual register

Builder-confident neutral — Vercel / Linear / Resend / Stripe-docs vocabulary. Monochrome base, single accent, geometric sans, generous whitespace, code and diagrams as design elements, very few stock images. Tailwind is the styling layer; component shapes lean shadcn-compatible so the website and the [[Webapp]] feel like siblings.

The register reinforces the [[Reference build]] framing — the website's job is to make the platform feel architected by serious software people. Warmer consumer-bank styling lives inside the [[Webapp]], not the marketing site.

## Evidence posture

Every claim on the page is either specific and verifiable, or it doesn't ship. The site links to live evidence wherever possible: the running [[Webapp]], the backend's `openapi.yaml`, real screenshots from the demo, and published [[Kensa]] BDD scenario reports that let a [[Visitor]] see actual cross-system behaviour described in `given/whenever/then`. The specific evidence inventory per section (what we link, what we embed, what we screenshot) is resolved in a separate grilling session.

### Kensa

The backend's BDD test framework (`given/whenever/then`). Tests describe real cross-service interactions and are part of every CI run. The backend pipeline publishes the end-to-end suite's report into this site's bucket under `/end-to-end-flows/` after every green run on main, and the architecture page links to it — a category of evidence no slideware competitor could fake. Publishing mechanics and constraints are in [ADR-0003](docs/adr/0003-kensa-reports-published-into-website-bucket.md).

## Functionality section

A short narrative paragraph framing _what this bank does_, then a 6–8 card capability grid. Each card carries a one-line claim and a one-line proof that links into the running [[Webapp]] where possible. Embedded inside the section is a short "follow the money" mini-walkthrough — open account → fund → FPS payment → CoP check → audit entry — as a secondary element, not the section's spine. If a step of the walkthrough breaks in the demo, we lose the mini-walkthrough but the cards still stand.

Capability set (subject to copy iteration): real-time payments (FPS), direct debits & standing orders, confirmation of payee, spending insights, balance forecast, multi-rail settlement (BACS, SWIFT), auditable double-entry ledger, modular & themeable platform. The ledger/audit and platform/theming claims are each held as a single card to keep the grid within the 6–8 cap enforced by the smoke test.

## Security section

Vertical _trust ledger_ — a list of standards-language claims, each with its evidence beside it: TLS at the edge, JWTs signed with a managed key and validated per request, append-only hash-chained audit, secrets in a managed store under per-task identity, RBAC enforced at the API, OAuth2 between services. The format mirrors how CISOs scan vendor security pages.

## Architecture section

Diagram-first — a single abstracted architecture diagram is the section's centrepiece. Component names are abstract per the brief (no cloud provider names, no internal service names). Annotations around the diagram surface the load-bearing patterns: cloud-native, event-driven, idempotency on sync and async paths, three-pillar observability, IaC for per-environment standup, CI/CD with smoke tests. A link to the public `openapi.yaml` lives here.

## Hero copy

> **A modern UK retail bank, end to end.**
> Malai is a working reference platform — not an authorised bank, holds no money, but the architecture, security and customer flows are real. Open an account in the demo, then read how it's built.
>
> [Try the demo →] [Talk to us]

The subhead's closing phrase _"then read how it's built"_ is a promise the page below honours. Section headings should land that pivot — section 1 answers _what it does_, section 2 _why it's safe_, section 3 _how it's built_.

## Navigation

A slim sticky top nav — Operations · Executive · Architecture · Security · About · What this isn't · Talk to us — with **Try the demo** as a primary button on the right. The desktop link list appears from the `lg` breakpoint (seven links outgrew `md`); below that a hamburger panel carries the same links.

## Content constraints

The brief is firm: the website never names the cloud provider (AWS) and never names internal application/service identifiers (e.g. `malai-api`, `malai-webapp`, `account-service`). Substitute _cloud provider_, _managed container platform_, _managed database_, etc. The forbidden term list lives at `docs/forbidden-terms.json` and is enforced two ways:

- A Playwright spec crawls every built page (driven by the sitemap) and fails if any forbidden term appears in rendered HTML.
- A CI lint step greps the same list against `*.md` and `*.astro` source so drafts fail before reaching review.

When new internal names are introduced anywhere in the workspace, they are added to the list.

## Footer

Three link columns — **Platform** (Operations, Executive, Architecture, Security, End-to-end flows), **Project** (About, Contact) and **Legal** (What this isn't, Privacy, Terms) — above the regulatory disclosure one-liner and the copyright line. No social links. The close-out section above the footer repeats the _Try the demo_ / _Talk to us_ CTAs.

## SEO, performance, accessibility

- `astro-seo` and `@astrojs/sitemap` are already in the dependency list and own meta and sitemap. Title: _Malai — a modern UK retail bank, end to end._ Description echoes the hero subhead. Single OG image.
- `@lhci/cli` is already wired. Lighthouse **Performance ≥ 95** and **Accessibility = 100** are ship gates — the accessibility target is non-negotiable for a bank pitch.
- Images: AVIF with WebP fallback. No client-side JS for the hero or for any section that isn't genuinely interactive. Astro islands are scoped to the genuinely-interactive surfaces only (probably the architecture diagram, if it becomes interactive).

## Language

UK English throughout. _Cheque_, _programme_, _organisation_, _centre_, _colour_. Money in GBP. Dates `DD MMM YYYY`. Copy review catches drift; we do not yet wire a dictionary check.

## Out of scope for v1

Explicitly not on the site, so we are not asked: no blog, no press page, no team page, no pricing page, no case studies, no cookie banner (no tracking shipped in v1; a privacy-respecting analytics like Plausible is the most we'd add later).

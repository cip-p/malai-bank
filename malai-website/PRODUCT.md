# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The site is written for one reader, the **Visitor**: a bank executive, a bank technologist (CTO, architect, platform lead, CISO), or anyone weighing an investment in a banking solution. Usually part of a buying committee, often arriving from a link shared into Slack, LinkedIn or email, sometimes during or after a live demo. Explicitly not a retail banking customer; the customer-facing experience lives in the demo webapp, not here.

The executive's job: understand what Malai is within five seconds, know immediately that it is not an authorised bank, try the demo in one click, judge whether the feature set is credible, and find a way to start a conversation. The technologist's job: scroll past the exec-friendly hero into substantive material, judge the architecture from one diagram, tick off table-stakes security patterns with evidence beside each, and verify by interacting with the running demo rather than reading promises.

Both should be able to disqualify or shortlist Malai without scheduling a call first.

## Product Purpose

The marketing site for Malai, a worked example of a modern UK retail bank. Malai is not a bank and holds no money; it exists so a Visitor can evaluate the underlying platform and engage the people who built it.

Success is the Visitor concluding _"this looks like a real bank, and the architecture beneath it is one we'd actually want to run"_, then registering for the demo or emailing the team. Honesty about what Malai is not is part of the pitch, not a footnote.

## Positioning

Malai is a **reference build**, and the offer is **engaging the builders** to adapt or recreate it for the buyer. The deliverables behind the offer are the architecture, code patterns, security posture and the running demo, delivered through a commissioned engagement with the team that built it. Confirmed 15 Sep 2026.

Not on offer: an off-the-shelf licensable package, a managed offering with SLAs, or white-label hosting. The hero, the site description and the About page once said "license what works, commission what's missing" and "white-label"; all three were corrected on 15 Sep 2026. Future copy must not repeat the claim.

What sets Malai apart: the demo is the real product (the Visitor self-registers into the same platform being sold), the platform's AI-assisted decisions live on an append-only, hash-chained Model Decision Record with human rulings appended to it, and cross-service behaviour is proven by published BDD scenario reports rather than described in a deck.

## Operating Context

- The website and the demo webapp together form the evaluation experience. The site links into the webapp's self-registration; an evaluator who registers is granted every role and can walk all three surfaces (Banking, Ops, Exec) behind one sign-in, with role separation enforced at the API.
- Primary call to action is demo registration. Secondary is "Talk to us", a mailto to the team's contact address. The About and What this isn't pages close with a two-step next-steps panel: register and explore the demo first, then talk to us, where the thirty-minute walkthrough over video call is offered through the same address. Decided 15 Sep 2026.
- Sign-up friction is accepted deliberately: the demo is the real product, and no separate website-only auth exists.
- Static site, deployed behind a CDN with a strict Content Security Policy (no inline scripts, no `'unsafe-inline'`). Nothing in the local toolchain serves that policy, so deployed behaviour is tested by replaying it in Playwright.
- The backend's deployment pipeline publishes its end-to-end BDD report into the deployed site under `/end-to-end-flows/`; the report is not part of this site's build.
- UK English throughout (cheque, programme, organisation, centre, colour). Money in GBP. Dates as `DD MMM YYYY`.

## Capabilities and Constraints

**Site shape.** Home page plus deep pages: `/operations`, `/executive`, `/architecture`, `/security`, `/about`, `/what-this-isnt`, `/privacy`, `/terms`, `/404`. Pages split by product surface, never by audience; every page addresses the Visitor. Home arc below the hero: Surfaces → Functionality → Security → Architecture, with Security and Architecture as teasers into their standalone pages. A close-out section repeats both CTAs above the footer. All copy is single-sourced in `src/content/*.ts`.

**Honesty constraints on copy.** Bound by what the backend actually does:

- Payment screening is a deterministic rules engine behind a model-shaped seam; no AI model is wired into screening.
- The Claude adapter behind the Copilot's seam is configuration-selected and off by default.
- The Copilot is read-only and advisory; no service consumes its output. Copy must never present it as a decision-maker.
- The executive metric is "payments stopped", never "fraud prevented".
- Every claim is specific and verifiable, or it does not ship.

**Naming constraints.** The public brand is **Malai**, never "MalaiBank" (regulated use of "Bank" under FSMA; the team is not authorised). "MalaiBank" appears only inside the demo webapp. The site never names the cloud provider or any internal service identifier; the canonical forbidden-term list is `docs/forbidden-terms.json`, enforced by a source lint and a rendered-HTML crawl. Structured data types Malai as a plain `Organization`, never `BankOrCreditUnion` or `FinancialService`, and carries the disclosure in `disambiguatingDescription`.

**Regulatory disclosure.** "Malai is a demonstration platform. Not a bank, does not accept deposits, and is not authorised by the FCA or PRA. All accounts and transactions shown are simulated." Lives in the hero pitch, the footer and the structured data. The What this isn't page states four binding lines (not a bank, not FCA-authorised, all data synthetic, no real payment rails) and overrides any other page that implies otherwise. A persistent top banner is not shipped by default.

**Technical constraints.** Astro 7 static output with React islands scoped to genuinely interactive surfaces only; no client-side JS for the hero or any non-interactive section. Tailwind 4 is the styling layer. Images ship as AVIF with WebP fallback. All scripts emitted as files so the deployed CSP holds. Internal links use the trailing-slash canonical form. Titles capped at 60 characters and meta descriptions at 158, enforced by tests. Prettier is a CI gate.

**Ship gates.** Lighthouse on the production build of the home page: Performance ≥ 95, Accessibility = 100, Best practices ≥ 95, SEO ≥ 95.

**Out of scope for v1.** No blog, press page, team page, pricing page, case studies, or cookie banner. Plausible is the only analytics the site would ever add; no tracking that requires consent.

**Open product facts.**

- The architecture page is meant to link the public `openapi.yaml`. The file is reachable at the demo origin today, but no page on the site links it yet.

## Brand Commitments

- Name: **Malai**. Title line: _Malai — a modern UK retail bank, end to end._ The live domain is `malaibank.com` and the contact address is `info@malaibank.com`.
- Voice: "we" / "the Malai team". No individual is named. No claims about team size; claims are about the work.
- Mark: shield and keyhole, applied to the favicon, OG image, header and footer here and mirrored in the demo webapp so the two feel like siblings.
- Binding visual constraint volunteered by ADR-0002 and recorded here without expansion: the register is _builder-confident neutral_, not institutional bank and not consumer fintech. Consumer-bank warmth belongs inside the webapp.
- The four-variant homepage redesign prototype under `/prototype/redesign` (ledger, broadsheet, aurora, dossier) is abandoned; the incumbent site is the design. Confirmed 15 Sep 2026. The prototype pages and components were deleted on 15 Sep 2026.

## Evidence on Hand

Real and reachable today (checked 15 Sep 2026, except where noted):

- Open demo registration at the webapp; anyone can self-register and receives every role.
- Published Kensa end-to-end BDD reports at `/end-to-end-flows/`, linked from the footer, the functionality section, the operations page and the architecture page.
- Twenty-one demo screenshots at `src/assets/screenshots/`, captured at 2560×1440: customer app (accounts overview, send payment, CoP check, fund account, cards, card purchase, direct debits, standing orders, account ledger, spending insights, balance forecast, payment referred), Ops surface (worklist, decision, ruling diff, chain, AML cases, AML case, AML SAR, copilot explanation) and the exec dashboard.
- `openapi.yaml` served by the demo origin, not yet linked from the site.
- Committed OG image, logo and favicon set generated from source SVGs.
- Seven ADRs under `docs/adr/` and the domain glossary in `CONTEXT.md`.
- The source code, in one public repository under the MIT licence: the backend, the webapp, this site and the infrastructure, with each module's `.scratch/` planning record. Copy may point Visitors to it; the Terms page already does. (Added when the repository went public.)

Absent, and never to be fabricated:

- No testimonials, customers, case studies, press, benchmarks, pricing, commercial licensing terms, or team roster.

## Product Principles

1. **Honesty is the pitch.** The disclosure sits in the hero, the What this isn't page overrides everything, and no page or machine-readable tag may imply bank status.
2. **Every claim ships with its evidence, or does not ship.** Link the running demo, the published reports and real screenshots; say plainly when evidence is pending.
3. **One reader, split by surface.** Every page addresses the Visitor; routes mirror the product's surfaces (Banking, Ops, Exec), never audiences.
4. **Evaluate the pattern, not the vendor.** Abstract component names, no cloud provider, no internal service identifiers.
5. **Disqualify or shortlist without a call.** Architecture, security posture and the commercial path are public before any conversation; the site never gates evaluation behind contact.

## Accessibility & Inclusion

Lighthouse Accessibility = 100 is a non-negotiable ship gate for a bank pitch. An axe-core Playwright scan runs at desktop and mobile viewports on every page as the first signal. Islands are kept off non-interactive sections so the page stays usable without JavaScript.

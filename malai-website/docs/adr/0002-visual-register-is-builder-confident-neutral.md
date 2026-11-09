# Visual register is builder-confident neutral, not institutional bank

The website's visual register is _builder-confident neutral_ — Vercel / Linear / Resend / Stripe-docs vocabulary. Monochrome base, single accent, geometric sans, generous whitespace, code and diagrams as design elements, very few stock images. We deliberately did **not** pick an institutional FT-style register (deep navy, serif, gravitas) nor a Monzo/Revolut-style consumer fintech register.

A future reader who knows Malai is a UK retail bank reference will reasonably ask _"why does it look like a developer tools site?"_ This ADR records the trade-off so that question is answered once, not re-litigated.

## Considered options

- **Institutional / FT-style.** Strongest "this is a serious bank vendor" first impression. Rejected because it fights what we actually are — a [[Reference build]], not a packaged bank product — and reads as old-guard against the modern stack the page describes.
- **Modern consumer fintech (Monzo / Revolut / Starling).** Warm, friendly, recognisably banking. Rejected because the [[Visitor]] is not a retail customer; styling the website like one frames Malai as a consumer app rather than a platform a bank executive would want to evaluate.
- **Builder-confident neutral (chosen).** Signals _"we're builders, and the platform speaks for itself."_ The register reinforces the pitch — when the [[Visitor]] sees code snippets, architecture diagrams and an `openapi.yaml` linkout, they feel native to the design rather than bolted on. The consumer-bank warmth lives where it belongs: inside the [[Webapp]].

## Consequences

- Tailwind is the styling layer; component shapes lean shadcn-compatible so the website and the [[Webapp]] feel like siblings without sharing a component library.
- Image budget is small. We do not source bank-stock photography; the architecture diagram and demo screenshots carry the visual load.
- If Malai is ever pivoted to be positioned as a real (or near-real) bank, this ADR's premise no longer holds and the register would need to be reconsidered.

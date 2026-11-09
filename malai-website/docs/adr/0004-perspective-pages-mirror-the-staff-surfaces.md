# Perspective pages mirror the product's staff surfaces

The platform grew beyond the customer app: an Ops surface built around the [[Model Decision Record]] (payment screening, human rulings, chain verification, the [[Copilot]]) and an Exec surface (the headline-numbers dashboard behind a deliberately narrow role). The original site shape — a single arc of Functionality → Security → Architecture, with "we do not split audience routes" on record — had nowhere to put them: they are not customer functionality, and burying them inside the security or architecture pages would hide the platform's strongest differentiator, the auditable AI-decision plane.

We add a home-page **Surfaces** section (three cards: Banking, Ops, Exec) and a standalone page per staff surface: `/operations` for the decision record and `/executive` for the dashboard. The audience does not change — every page is still written for the [[Visitor]] — the split is by product surface, because each surface carries its own evidence: screenshots of the live worklist, decision replay, ruling diff, chain verdict and dashboard.

## Considered options

- **Fold the new material into existing pages.** No new routes, but the decision record becomes a paragraph inside a page about something else, and the three-surface shape of the product — the thing a buyer must understand first — never gets stated.
- **One combined "Platform" page for both staff surfaces.** Fewer routes, but Ops and Exec argue different things (auditability vs separation of duties) to different readers in the buying committee, and the Exec story would sit below five screenshots of Ops.
- **A page per surface (chosen).** The home page states the three-surface shape and stays scannable; each staff page goes deep with its own evidence. Banking keeps the existing functionality section rather than gaining a third page — it was already the home page's spine.

## Consequences

- New routes `/operations` and `/executive`; header and footer navigation grow to include them, and the desktop nav list now appears at `lg` instead of `md` (seven links no longer fit at `md`).
- The home-page arc becomes Surfaces → Functionality → Security → Architecture, and CONTEXT.md's site shape is updated to match.
- Screenshot inventory now includes staff-surface captures (worklist, decision record, ruling diff, chain verification, executive dashboard, referred payment). They follow the existing 2560×1440 convention.
- Copy on these pages is bound by honesty constraints from the backend's record: screening is a deterministic rules engine behind a model-shaped seam (no AI model is wired); the Claude adapter behind the Copilot's seam is configuration-selected and off by default; the exec metric is "payments stopped", never "fraud prevented".
- The smoke tests' hard-coded nav and footer expectations were updated in lockstep; the trust ledger count moved from 16 to 18.

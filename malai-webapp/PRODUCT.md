# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Two audiences of equal weight, both bank-side, never a retail customer:

- **A prospect exploring unaided.** A bank executive or technologist who registers with only a name and email, verifies by emailed code, and pokes around with nobody narrating. They judge every screen against their own bank's app and back office. First visits must be self-explanatory: discovery of the Ops and Exec surfaces and the Sandbox, honest empty and pending states, no dead ends.
- **A presenter driving a rehearsed walkthrough.** Malai's own person walks bank decision-makers through headline moments (freeze a card and watch the decline, park a payment in screening and rule on it in Ops, fire an AML structuring pattern and draft a SAR, switch the customer brand and show the back office unchanged). Screens must land those beats without a reload and survive being projected.

The job in both cases is evaluation: _does this look like a real UK bank, and is the platform beneath it one we would want to run?_ The end user is never the person whose money is on screen.

## Product Purpose

Malai is a reference UK retail banking platform, built so prospective bank operators can evaluate the architecture before commissioning it. The webapp is its customer-facing and staff-facing product: one registered login opens three sibling Surfaces, the **Banking surface** (`/`), the **Ops surface** (`/ops`) and the **Exec surface** (`/exec`), each Offered or Withheld by the Roles the User holds. Success is a bank decision-maker concluding the demo is indistinguishable from a production bank and that the architecture is one they would commission. Anything that does not move that conclusion forward is not v1.

## Positioning

All four of these are binding claims, not aspirations:

1. **Three Surfaces, one record plane.** Customer banking, the MDR back office and the executive numbers sit behind one login and read and write the same Model Decision Record and audit chain. The platform is visibly one thing.
2. **Ruled, explainable automation.** Screening, AML alerts, SAR drafts and the copilot never act alone. A human ruling gates every actionable output, a refusal is rendered as a fact rather than an error, and a grounding failure is surfaced with its raw output still readable on the record.
3. **A real-bank-grade customer app.** UK retail-banking conventions (Confirmation of Payee, Direct Debits, Standing Orders, virtual cards with freeze and replacement, month-end balance forecast, available-balance-first) done to production polish, so an executive cannot tell it from their own.
4. **Brand as a skin.** The theme catalogue of three fictional bank brands proves a prospect's own identity could sit on top while the staff Surfaces stay constant. Brands are skins; the record plane is the platform's.

## Operating Context

- Talks directly to `malai-api` over HTTP with cookie-based auth; there is no offline mode. When the API is Unreachable the whole app becomes the **Paused state** (a designed page in the bank's voice, not an error screen) and recovers by itself.
- Passwordless login: email plus an emailed Confirmation code. Registration takes first name, last name and email only; address and date of birth are Generated details.
- Every registered User in the demo grant holds every Role, so one person can play customer, ops reviewer and executive in one session. Seeded single-role users (ops reviewer, customer-only, manager-only) exist to prove the gating is real.
- The **Sandbox** (`/sandbox`, a peer of Settings) is where the demo's controllable state lives: the fictional counterparty directory with its CoP behaviour, simulated inbound payments, card purchase / present-now / refund triggers. It is presented as a platform-vendor sandbox tier, never as "demo mode".
- Locally, `npm run dev` serves the app on `:5173` against a local `malai-api` on `:8080`.
- Installable as a PWA (manifest, icons, splash) with no service worker; portrait-primary on phones, full desktop layout otherwise. Mobile uses bottom tabs, desktop a top nav.
- Analytics via Plausible.

## Capabilities and Constraints

**Banking surface**: accounts led by available balance (ledger balance never rendered), transaction history with pending and declined card rows, Send money with CoP and press-through override, Payees (create and delete, no edit), Direct Debits and Standing Orders as read-only lists on separate screens, per-account balance forecast to month-end, cards (reveal, freeze/unfreeze, report lost or stolen), credit card application and Pay card, settings, theme and mode switching, Sandbox. Payment outcomes: referred (polls, says nothing about why) and declined (a refusal with no reason, never the word "declined").

**Ops surface**: the Worklist of Model Decisions with verdict on the row, the Decision view as a record (nothing editable, rulings append), the Gate panel, the Chain page (verification on demand), the Case list and Case view for AML with the SAR panel and two Close actions, copilot explanations in the Advisory view.

**Exec surface**: four Metric tiles (new customers, deposits, cards issued, payments stopped) over a Reporting window anchored to the London banking day. A tile shows a real figure or an em-dash, never a zero standing in for an absent number.

**Constraints future work must keep**:

- The OpenAPI contract owned by `malai-backend` is the source of truth; the UI renders what the API says and never invents a field, a reason, or a status.
- Frontend Roles only decide which Surfaces are Offered; enforcement is at the API. A Withheld Surface renders the Access page, never a 404 or a silent redirect.
- Error codes are not translated into friendlier prose on customer surfaces (anti-enumeration), with two narrow carve-outs: insufficient funds inline, and Ops-surface refusal codes mapped to domain facts.
- Staff Surfaces pin the default token set and offer no theme switcher; only Mode is shared with Banking.
- No code comments anywhere, including tests. Testing is first-class: Vitest unit and integration tests.
- Terminology is fixed in `CONTEXT.md` (Surface, Role, Offered/Withheld, Account vs AccountHolder vs User, Transfer in code / "Send money" in copy, Hold, Referred, Declined qualified, Worklist, Decision view, Case view, Metric tile). New copy speaks that language and honours its avoid-lists.
- Retired claim: "fraud prevented". The tile is "Payments stopped" because the decider is a rules engine and a declined payment is money the bank stopped, not proven fraud.

**Exploratory**: `src/prototype-redesign` holds three redesign concepts (Ledger workspace, Pocket card deck, Atrium split-screen). They are routed only in the dev server, at `/prototype/redesign`, and production builds leave them out.

## Brand Commitments

- The deployment is **malai**: lower-case wordmark, favicon and PWA icons are malai-only regardless of the active theme. Manifest tagline: "malai — a UK retail bank for the modern era." Theme colour `#913814` on a `#fef9f1` canvas.
- The theme catalogue is three named fictional bank brands, each with its own wordmark SVG, light and dark Modes, and WCAG AA contrast on both: `malai` (default), `halcyon` (fintech-challenger), `linden` (incumbent-heritage). Themes are brands, never colour names.
- **VELA** is the fictional card-scheme mark on all card art. Real scheme names and marks are never used.
- Typography is app-wide and not themed: Inter, self-hosted, with tabular figures on every money-bearing component.
- Voice: the bank speaks in its own voice, calm and specific. A refusal is phrased as a decision ("We didn't make this payment"), never as a failure or an inability, and never with a reason. A paused service is stated plainly, not apologised for.

## Evidence on Hand

Nothing real exists to cite. There is no pilot bank, named customer, testimonial, press, benchmark, or regulatory approval. Every brand, person, counterparty and merchant is fictional, and every figure on screen must come from the live API. Future work must not fabricate any of these, must not show a hardcoded number that could end up in a screenshot, and must not present the product as a regulated bank. Real assets that do exist: the malai / halcyon / linden wordmarks and the malai icon set under `public/` and `src/theming`.

## Product Principles

1. **Indistinguishable from a real bank, at every altitude.** The customer app, the back office and the exec numbers are each judged against the evaluator's own; the demo framing surfaces only in the Sandbox.
2. **The record rules, the UI displays.** Rulings append, nothing edits, refusals are facts, and the client never predicts what the Gate will decide.
3. **Honest over reassuring.** An em-dash beats a zero, a paused page beats a spinner, a generic error beats a helpful lie, and a grounding failure is shown with its raw output.
4. **One platform, many skins.** Switching the customer brand must leave the staff Surfaces visibly unchanged; that constancy is a pitch beat, not a limitation.
5. **Every screen survives an unguided first visit.** A prospect who registers alone must find the three Surfaces and the Sandbox without a narrator.

## Accessibility & Inclusion

The audience judges against the Equality Act 2010 and FCA expectations for digital services, so WCAG 2.1 AA is the standard from day one (ADR-0006): focus moves to the page `<h1>` on route change with a skip link, submit errors focus the first invalid field, all motion respects `prefers-reduced-motion`, every theme token pair meets AA in both Modes, and sonner toasts are the only shell-level live region, with polite regions added only where a fact must announce itself.

# 04 — Functionality section: capability cards + "follow the money" walkthrough

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The first below-the-hero section, answering _"what does this bank actually do?"_ for the exec [[Visitor]]. The section is structured as:

1. A short narrative paragraph framing the bank in the [[Visitor]]'s terms. Two to four sentences; confident, plain, builder-neutral voice consistent with the hero.
2. A **6–8-card capability grid**. Initial capability set:
   - Real-time payments (FPS)
   - Direct debits & standing orders
   - Confirmation of Payee
   - Double-entry ledger
   - Multi-rail settlement (BACS, SWIFT)
   - Themeable mobile-first app
   - Modular feature additions
   - Auditable money movement

   Each card carries a one-line claim (what the capability is) and a one-line proof (how a [[Visitor]] can verify it — a link into the running [[Webapp]] where possible, otherwise a brief factual statement). Card copy is AFK-drafted in the builder-confident neutral voice and reviewed at merge.

3. A **"follow the money" mini-walkthrough** embedded inside the section as a secondary element. A short, named sequence: open account → fund → FPS payment → CoP check → audit entry. Each step is one sentence and (where possible) carries an inline link into the live demo at the matching screen. The walkthrough is structurally distinct from the card grid so a future evidence pass can swap in real screenshots without rearranging the section.

The section mounts under the `#functionality` anchor so `StickyNav` from Issue 03 can target it.

Content for cards, walkthrough steps, and the narrative paragraph lives in the `content` module. Components consume from there; no string is inlined into markup.

The page-smoke Playwright spec extends to assert: the section is present, every card from the content collection appears in the rendered DOM with its claim and proof, the walkthrough has its expected step count, the section `id` matches the nav anchor.

No client-side JS may be added by this section — it is pure markup over content.

Forbidden terms must not appear in the section, the cards, or the walkthrough. The brand on the section is _Malai_, never _MalaiBank_.

## Acceptance criteria

- [ ] A `FunctionalitySection` Astro component exists and is mounted in `index.astro` under an element with `id="functionality"`.
- [ ] The `content` module gains typed entries for: the section narrative paragraph, the array of capability cards (each `{title, claim, proof, href?}`), and the walkthrough steps (each `{step, text, href?}`).
- [ ] The rendered section contains the narrative paragraph, 6–8 capability cards matching the initial set, and the named "follow the money" walkthrough.
- [ ] Card "proof" links resolve to the running [[Webapp]] where the PRD notes a verifiable target; otherwise the proof is a factual one-liner.
- [ ] Page-smoke spec extends to cover section presence, card count and content, walkthrough step count.
- [ ] No client-side JS hydrated by this section.
- [ ] No forbidden term appears in rendered HTML for this section.
- [ ] Lighthouse performance and accessibility numbers do not regress relative to the post–Issue 01 baseline (final gates land in Issue 08; this slice should not introduce known regressions).

## Blocked by

- `01-walking-skeleton.md` — needs the layout, content module, and page composition.

Issues 02 and 03 may merge before or after this slice; this issue does not depend on them, but `findForbidden(html)` from Issue 02 and the `#functionality` anchor consumer in Issue 03 both compose cleanly with this slice once they land.

# 09 — Architecture diagram artwork

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

Replace the placeholder architecture diagram from Issue 06 with a coherent abstracted SVG diagram suitable for the Architecture section. The diagram is not high-design artwork — its job is to make the technologist [[Visitor]] glance at the section and read it as honest, with a clear system shape they can map their own bank against.

The diagram must:

- Be authored as inline SVG (or an Astro component that renders SVG) — not a raster image. Inline SVG keeps file size small, scales cleanly, inherits the page's typography for labels, and lets the forbidden-terms Playwright crawl reach the text.
- Use only **abstract** component names: _Edge / TLS termination_, _API gateway_, _Identity_, _Account domain_, _Payments domain_, _Audit domain_, _Managed database_, _Event bus_, _Managed object store_, _Image registry_, _External rails (FPS · BACS · SWIFT · CoP · Ledger)_. No cloud provider names, no internal service identifiers — the same forbidden-terms list from Issue 02 applies to every label rendered inside the SVG.
- Show flow direction with arrows: synchronous request paths in one visual style (solid lines), asynchronous event paths in another (dashed). The legend is part of the diagram, not a separate element.
- Match the builder-confident neutral visual register from ADR-0002 — monochrome strokes, one accent colour used sparingly (e.g. for the external-rails block or the event bus), generous whitespace, geometric sans for labels (inheriting from the page's typography).
- Be legible at section widths from ~360px (mobile) to ~1100px (desktop). On mobile this likely means a single-column layout of the same components with rearranged flow lines — produce a responsive SVG or a small adaptive variant rather than a desktop-only image that pinches.
- Carry an `aria-label` and a `<title>` / `<desc>` block describing the diagram so screen readers receive a meaningful summary. This is required for the Issue 08 a11y spec to pass.

When this slice ships, the placeholder from Issue 06 is removed; the content collection's reference to the diagram artwork is updated to point at the real asset.

The page-smoke spec from earlier issues should continue to pass — labels still match what tests assert. The a11y spec from Issue 08 must also pass against the new diagram.

The forbidden-terms Playwright crawl from Issue 02 protects against accidental leaks; if a label needs to convey a cloud-provider concept, use the abstract equivalent (e.g. _Managed container platform_ rather than the AWS service name).

## Acceptance criteria

- [ ] The placeholder diagram from Issue 06 is replaced with a real abstracted SVG diagram authored according to the rules above.
- [ ] All labels use abstract component names; the forbidden-terms list from Issue 02 catches nothing in rendered HTML.
- [ ] Synchronous and asynchronous flow lines are visually distinguished with a legend present in the diagram.
- [ ] The diagram remains legible at 360px width and ~1100px width — either as a single responsive SVG or as a small adaptive variant chosen by viewport.
- [ ] The SVG carries a meaningful `<title>` / `<desc>` block and the section's container carries an `aria-label`.
- [ ] The page-smoke spec, the forbidden-terms crawl, and the a11y spec all pass against the updated diagram.
- [ ] The diagram's visual register is consistent with ADR-0002 — monochrome base, single accent used sparingly, geometric sans labels inheriting from page typography.

## Blocked by

- `06-architecture-section.md` — replaces the placeholder this issue introduces.
- `08-accessibility-and-lighthouse-gates.md` — a11y spec must pass against the new diagram; lands after the gate exists so the work is verified against the same bar everything else is.

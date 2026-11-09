# Theme catalogue is three fictional bank brands, not generic palettes

[ADR-0003](./0003-theming-via-css-variables-and-token-discipline.md) commits to the _architecture_ of theming (shadcn CSS variables, one `[data-theme="..."]` block per theme, token discipline enforced by ESLint). This ADR fills in _what ships in v1_: three named fictional bank brands — `malai` (the default — cream canvas, espresso ink, warm accent), `halcyon` (navy fintech-challenger), `linden` (forest-green incumbent-heritage). Each ships both light and dark Modes. Each comes with a wordmark SVG that the AppShell header swaps when the active theme changes.

The framing matters for the sales pitch. The malai audience is bank decision-makers; the demo's job is to make them think _"our brand could sit here"_. Naming themes after fictional banks with distinct archetypes (premium-modern, modern-safe, established-heritage) lets a prospect project their own bank onto whichever archetype matches them. Naming them after colours (`blue` / `emerald` / `aubergine`) collapses the framing into a colour picker and loses the pitch.

## Considered options

- **Three fictional bank brands, named and identity-rich (accepted).** Strongest demo framing. Per-theme work is real but bounded; three is the ceiling at which the ADR-0006 #4 manual contrast review still scales.
- **Generic colour-named palettes.** Cheapest. Loses the "your bank could look like this" framing — switcher reads as cosmetic, not strategic.
- **Two themes (malai + one alternative).** Proves the architecture but reads as a tech demo, not a feature; gives the audience little to compare against.
- **Five or more themes.** Overinvests. Breaks the manual contrast review (ADR-0006 #4), and the catalogue starts to read as a colour picker again.

## Consequences

- Each new theme is now scoped work: palette + `--radius` + light + dark + a wordmark SVG + a manual AA contrast pass on both Modes. Adding a fourth is a real commitment, not a one-line append.
- The favicon remains `malai`-branded only — it represents the deployment, not the active theme. See [ADR-0013](./0013-pwa-manifest-no-service-worker.md) for the manifest icon set, which is also `malai`-only.
- The "primary accent" in each theme is the only literal-brand-colour leak point. ESLint enforcement (ADR-0003) still applies: components reference `bg-primary`, never `bg-amber-500`.

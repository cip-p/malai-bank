# Typography is app-wide and not themed

The webapp loads exactly one typeface — **Inter**, self-hosted from `public/fonts/` (the Latin and Latin Extended subsets of the variable woff2, taken from `@fontsource-variable/inter` 5.2.8, with the Latin file preloaded from `index.html`) — and applies it globally. A **Theme** ([ADR-0011](./0011-theme-catalogue-as-fictional-bank-brands.md)) varies palette and `--radius`; it does _not_ vary font family, weight scale, or type ramp. Money-bearing components (balances, transaction amounts, account totals) additionally set `font-variant-numeric: tabular-nums` so columns of figures align — this is what real banking apps do and is one of the cheapest cues that signals "designed by a bank".

The reason to keep typography out of the theme axis is cost. Per-theme typography means font loading per theme (or up-front loading of every theme's font), FOUT prevention on theme switch, and a per-theme legibility-at-size review on top of the existing per-theme AA contrast review ([ADR-0006](./0006-a11y-baseline.md) #4). For three themes the cost roughly triples — for marginal demo lift, because palette+radius is already enough to make the three brands feel distinct.

## Considered options

- **Single app-wide variable sans (Inter), no per-theme typography (accepted).** One font load. Tabular-nums on money components. Marginal cost; sufficient brand differentiation when combined with palette + `--radius`.
- **System font stack.** Free, instant. Reads as "developer default", not "designed bank" — undercuts the demo polish the brief asks for.
- **Per-theme font family (`--font-sans` token, themed alongside palette).** Strongest brand differentiation. ~3x font-loading work, FOUT-on-switch handling, per-theme legibility review. The demo lift over palette-only theming is real but small.
- **A more distinctive sans (Manrope / Plus Jakarta / DM Sans).** More personality than Inter. Risk of reading as "designer" rather than "any bank" — narrows the audience fit the demo is built for.

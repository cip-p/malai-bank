# Theming via shadcn CSS variables + token discipline

Theming is part of the malai sales pitch — bank decision-makers should see "this could look like _our_ bank" within a single demo. We theme via shadcn's CSS-variable convention: each named theme (e.g. `malai`, `emerald-bank`) lives as a `[data-theme="..."]` block in `src/styles/themes.css` defining the full token set (`--background`, `--primary`, `--radius`, etc.); `next-themes` toggles light/dark orthogonally; a demo theme switcher swaps the `data-theme` attribute on `<html>`.

To make rebrand truly cheap, **components must never reference literal Tailwind palette colors** (`bg-blue-500`, `text-emerald-600`, `border-zinc-300`). They use tokens (`bg-primary`, `text-muted-foreground`, `border-border`). The ban is enforced by an ESLint rule on Tailwind classes — if you find yourself wanting a literal palette colour, the right move is to add a token, not to leak the literal. Without this rule, the second theme will surface hundreds of leaked literals; with it, adding a theme is one new `[data-theme="..."]` block.

## Considered options

- **shadcn CSS-vars + token discipline (accepted).** Cheap N-theme story; minor upfront cost (one ESLint rule, one `themes.css`).
- **Single theme for v1, generalise later.** Honest YAGNI; rejected because the rebrand pitch is the demo's value, and retrofitting "no literal colors" across an established codebase is far harder than enforcing it from day one.
- **Tailwind config theme objects.** Heavier per-theme cost, weaker fit with shadcn's existing variable idioms.

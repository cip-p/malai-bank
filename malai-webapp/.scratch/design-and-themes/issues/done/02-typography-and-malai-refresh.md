# Typography (Inter) + malai Theme token refresh

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The app-wide visual baseline that every other slice inherits. After this slice lands, the whole app renders in Inter with a refreshed malai brand palette — no layout changes, no new features, just a different look that propagates through every existing page via [ADR-0003](../../docs/adr/0003-theming-via-css-variables-and-token-discipline.md) token discipline.

- Self-host **Inter** via `@fontsource/inter` (variable axes). Wire it into the global stylesheet so `body` inherits it; expose a `--font-sans` token consumed by `@theme inline` so Tailwind's `font-sans` utility maps to Inter. No FOUT-mitigating CSS hacks; the font is self-hosted and synchronous-ish.
- Refresh the `[data-theme="malai"]` and `[data-theme="malai"].dark` blocks to lean into the brand's meaning (Hindi/Urdu for _cream_): warm cream canvas, deep espresso ink, one saturated warm accent for `--primary`. Every shadcn token in the block is retuned for the new direction. The dark block uses the same accent against a deep ink canvas. Both meet WCAG AA contrast on every paired token per [ADR-0006](../../docs/adr/0006-a11y-baseline.md) #4 — record the contrast pass results in the issue thread.
- Set `--radius: 0.625rem` (or whatever value matches the Starling/Chase-aligned soft-radius direction — agent's call within `0.5rem`–`0.75rem`).
- A new `tabular-nums` utility (or just a Tailwind class application convention) is documented for use by money components in later slices — no money components are introduced here.
- No layout changes to any page. Every existing page inherits the new typography and palette via the ADR-0003 token discipline that's already in place.
- See [ADR-0011](../../docs/adr/0011-theme-catalogue-as-fictional-bank-brands.md) for the brand framing and [ADR-0012](../../docs/adr/0012-typography-app-wide-not-themed.md) for why typography is not in the theme axis.

## Acceptance criteria

- [ ] `@fontsource/inter` installed and imported once; Inter renders on every page (verify by computed font-family in a dev-tools snapshot or a Playwright check).
- [ ] `--font-sans` declared in `:root` and wired through `@theme inline` so the Tailwind `font-sans` utility maps to Inter.
- [ ] `[data-theme="malai"]` light block redefines every token to the cream-canvas + espresso-ink + warm-accent direction.
- [ ] `[data-theme="malai"].dark` block redefines every token to the deep-ink-canvas + warm-accent direction.
- [ ] Every paired token (background/foreground, card/card-foreground, primary/primary-foreground, muted/muted-foreground, accent/accent-foreground, destructive/destructive-foreground) meets WCAG AA (≥4.5:1 for normal text, ≥3:1 for large text) in both modes. Contrast results recorded as a comment on the issue file.
- [ ] `--radius` updated within the agreed range; affects every shadcn primitive that reads it.
- [ ] No literal Tailwind palette colours leaked into any component (the existing ADR-0003 ESLint rule passes).
- [ ] All existing unit tests pass unchanged.
- [ ] All existing Playwright tests pass unchanged (no layout regressions).
- [ ] No comments anywhere.

## Blocked by

None — can start immediately. Independent of issue 01.

## Comments

### WCAG AA contrast pass — 2026-06-05

Computed via oklch → linear sRGB → relative luminance → WCAG 2.x contrast for every paired token in `src/styles/themes.css`. AA-N = passes 4.5:1 normal-text threshold.

**Light (`[data-theme="malai"]`):**

| Pair                                 | Ratio   | Verdict |
| ------------------------------------ | ------- | ------- |
| background / foreground              | 17.23:1 | AA-N    |
| card / card-foreground               | 18.00:1 | AA-N    |
| popover / popover-foreground         | 18.00:1 | AA-N    |
| primary / primary-foreground         | 7.34:1  | AA-N    |
| secondary / secondary-foreground     | 14.45:1 | AA-N    |
| muted / muted-foreground             | 7.25:1  | AA-N    |
| accent / accent-foreground           | 13.32:1 | AA-N    |
| destructive / destructive-foreground | 6.98:1  | AA-N    |

**Dark (`[data-theme="malai"].dark`):**

| Pair                                 | Ratio   | Verdict |
| ------------------------------------ | ------- | ------- |
| background / foreground              | 16.78:1 | AA-N    |
| card / card-foreground               | 15.46:1 | AA-N    |
| popover / popover-foreground         | 15.46:1 | AA-N    |
| primary / primary-foreground         | 7.83:1  | AA-N    |
| secondary / secondary-foreground     | 13.88:1 | AA-N    |
| muted / muted-foreground             | 6.75:1  | AA-N    |
| accent / accent-foreground           | 11.39:1 | AA-N    |
| destructive / destructive-foreground | 5.57:1  | AA-N    |

All paired tokens clear the WCAG AA 4.5:1 normal-text threshold per [ADR-0006](../../../docs/adr/0006-a11y-baseline.md) #4.

### Notes

- `--radius` set to `0.625rem` (top of the 0.5–0.75 band, matches the Starling/Chase-class soft-radius direction).
- `--font-sans` declared on `:root` and re-exposed through `@theme inline` so `font-sans` Tailwind utilities map to Inter Variable.
- A `.tabular-nums` utility ships in `src/index.css`; money components introduced by issue 03 will pick it up via `<Money>`.
- No layout or component-level changes — every existing page inherits the refreshed tokens and typography automatically.

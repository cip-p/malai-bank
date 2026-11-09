# Theme catalogue: useTheme + Halcyon + Linden + wordmarks + desktop ThemeSwitcher

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The rebrand feature itself. After this slice lands, a signed-in **User** on a desktop can open a header dropdown next to the user menu, pick between three named **Themes** (malai / halcyon / linden), and the entire app rebrands in front of them — palette, `--radius`, and the header wordmark all swap. The choice persists across reloads on that browser. See [ADR-0011](../../docs/adr/0011-theme-catalogue-as-fictional-bank-brands.md).

- **`useTheme` hook + `<ThemeProvider>`** — deep module exposing `{ theme, setTheme, themes }`. Reads from `localStorage` on mount; falls back to `malai` when the stored value is missing or not in the known set. Every `setTheme` writes to `localStorage` and updates `document.documentElement.setAttribute('data-theme', theme)`. Independent of `next-themes`, which continues to own the dark/light **Mode** axis per [ADR-0003](../../docs/adr/0003-theming-via-css-variables-and-token-discipline.md).
- **Halcyon theme tokens** — `[data-theme="halcyon"]` and `[data-theme="halcyon"].dark` blocks. Identity: navy fintech-challenger (Starling- / Chase-aligned). Full shadcn token set including `--radius`. Both modes meet WCAG AA contrast per [ADR-0006](../../docs/adr/0006-a11y-baseline.md) #4 — record the contrast pass results in the issue thread.
- **Linden theme tokens** — `[data-theme="linden"]` and `[data-theme="linden"].dark` blocks. Identity: forest-green incumbent-heritage (Lloyds-adjacent). Same contrast rule.
- **Three wordmark SVGs** — one each for `malai`, `halcyon`, `linden`. Each renders the brand name in a styled wordmark and uses the theme's accent token (`currentColor` driven from a parent that sets `color: var(--primary)`) so it always reads in-brand.
- **`<HeaderWordmark>`** — looks up the active Theme via `useTheme` and renders the matching SVG. Used in the `AppShell` header and (later) on the auth surface.
- **`ThemeSwitcher` dropdown** — uses `radix-ui` `DropdownMenu`. Mounted in the desktop header next to the existing user menu. Each item shows the theme name, a colour swatch (the theme's `--primary`), and a checkmark on the active item. Selecting an item calls `setTheme`. On mobile (below `md:`), the switcher in the header is hidden — it will be surfaced inside the Profile tab by [09](./09-profile-settings-and-mobile-switcher.md).
- The favicon is **unchanged** by this slice — it remains the static malai mark per [ADR-0013](../../docs/adr/0013-pwa-manifest-no-service-worker.md). Theme switching does not touch `<link rel="icon">`.

## Acceptance criteria

- [ ] `useTheme` Vitest unit tests cover: default theme = `malai` when storage is empty; `setTheme('halcyon')` updates `document.documentElement[data-theme]` and writes to `localStorage`; rehydration from `localStorage` restores the theme on mount; unknown stored values fall back to `malai`.
- [ ] `[data-theme="halcyon"]` and `[data-theme="halcyon"].dark` blocks define every shadcn token; every paired token meets WCAG AA in both modes (contrast results recorded on the issue).
- [ ] `[data-theme="linden"]` and `[data-theme="linden"].dark` blocks define every shadcn token; every paired token meets WCAG AA in both modes (contrast results recorded on the issue).
- [ ] Three wordmark SVGs ship and render in the active theme's accent colour.
- [ ] `<HeaderWordmark>` swaps the rendered SVG when the active theme changes — no reload, no flicker.
- [ ] Desktop `ThemeSwitcher` dropdown lists the three Themes with the active one marked; selecting an item applies the theme app-wide and persists it.
- [ ] Switcher hidden at mobile breakpoints (Slice 09 will surface it inside the Profile tab).
- [ ] Component test for the switcher covers: opening the dropdown, selecting a theme, asserting on `document.documentElement[data-theme]` and `localStorage`.
- [ ] Favicon (`<link rel="icon">`) is not touched.
- [ ] axe-core a11y spec extended over the open dropdown without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md) (switcher mounts in the AppShell header next to the user menu)
- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md) (the refreshed malai theme is the template Halcyon and Linden are built against)

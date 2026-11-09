# malai-website

The marketing site for Malai, a reference UK retail banking platform. It's a static site built
with Astro 7, with React islands only where a page is genuinely interactive, and styled with
Tailwind v4.

## Run locally

```bash
npm ci
npm run dev                # dev server on http://localhost:4321
```

To check the production build instead:

```bash
npm run build              # static output into out/
npm run preview            # serve out/ on http://localhost:4321
```

## Checks

| Command                   | What it runs                                                                                                                                                                                                                                                                                                                        |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run prettier`        | Prettier in check mode. `npm run prettier:fix` rewrites.                                                                                                                                                                                                                                                                            |
| `npm run typecheck`       | `astro check` against the project sources.                                                                                                                                                                                                                                                                                          |
| `npm run lint:forbidden`  | Scans `src/**/*.{astro,ts,tsx,md,mdx}` for any term in `docs/forbidden-terms.json`. Non-zero exit on a hit.                                                                                                                                                                                                                         |
| `npm run test:unit`       | Vitest unit tests in `tests/unit/`.                                                                                                                                                                                                                                                                                                 |
| `npm run test:e2e`        | Playwright specs in `tests/e2e/` against the production build, served by `astro preview`. Includes the sitemap-driven forbidden-terms crawl, the `@axe-core/playwright` accessibility scan, and a replay of the production content security policy. Run `npm run build` first, and `npm run test:e2e:install` once to get Chromium. |
| `npm run test:lighthouse` | Lighthouse CI ship gates against the production build in `out/`.                                                                                                                                                                                                                                                                    |

## Ship gates

The home page is held to four Lighthouse scores. The thresholds live in
[`lighthouserc.json`](lighthouserc.json) and are enforced by `npm run test:lighthouse`:

| Category       | Minimum score |
| -------------- | ------------- |
| Performance    | 95            |
| Accessibility  | 100           |
| Best practices | 95            |
| SEO            | 95            |

Lighthouse runs against the production build in `out/`, not the dev server. It runs locally
only. Performance scores vary too much on shared CI runners to make a reliable gate. The axe
spec in `tests/e2e/accessibility.spec.ts`, which CI does run, catches accessibility regressions
first.

## CI

`.github/workflows/malai-website.yml` has two jobs:

- **build** runs formatting, type-check, forbidden terms, unit tests and the build.
- **smoke-tests** builds the site and runs the Playwright specs against it.

## Forbidden terms

The site's copy never names the cloud provider or internal service identifiers. The canonical
list lives at [`docs/forbidden-terms.json`](docs/forbidden-terms.json). Append to it when a new
internal name appears anywhere in Malai.

Enforcement is two-layered:

- **Source:** `npm run lint:forbidden` greps the list against `src/**/*.{astro,ts,tsx,md,mdx}`.
  CI runs it before the build.
- **Rendered output:** a Playwright spec crawls every page in the built sitemap and asserts
  `findForbidden(html) === []` for each.

Both import the same `findForbidden()` from `src/lib/forbidden-terms.ts`.

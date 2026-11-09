# SEO metadata is centralised, and structured data never claims bank status

The site already carried per-page titles, canonicals and a sitemap, but three things were missing or wrong. `og:image` pointed at an SVG, which every major social platform refuses — so a link to Malai pasted into Slack, LinkedIn or an email preview rendered with no card at all, on a site whose whole distribution model is being shared into a buying committee. There was no `robots.txt`. And each page's meta description reused its on-page narrative — 160 to 400 characters of body prose, truncated by search engines well before the sentence resolved.

We centralise every indexable page's title and description in `src/content/seo.ts`, alongside the existing content objects, and add machine-readable identity via a JSON-LD `@graph` in `StructuredData.astro`.

## Considered options

- **Per-page literals in each `.astro` file.** Where the titles already lived. Cheap, but descriptions drift into duplicates and length regressions with nothing to catch them, and no one place shows whether the set reads coherently in a search result.
- **Derive descriptions from body copy.** What the site did. Single-sourced and always accurate, but body prose is written to be read in place, not truncated at 155 characters — every page overran, some by more than double.
- **A central `seo.ts` map (chosen).** Titles still interpolate the section headings where those exist, so the surface pages stay single-sourced, while descriptions are written for the search result. The whole set is reviewable in one file, and tests assert length, uniqueness and presence.

## The bank-status constraint

Schema.org has types that fit a retail bank exactly — `BankOrCreditUnion`, `FinancialService`. Using one would be a machine-readable assertion, to search engines and any agent consuming the page, that Malai is a regulated deposit-taker. That is the precise claim [[ADR-0001]] and the footer disclosure exist to prevent, and unlike prose it is not something a reader can contextualise.

Malai is therefore typed as a plain `Organization`. The disclosure travels with it in `disambiguatingDescription` — "Not a bank, does not accept deposits, and is not authorised by the FCA or PRA" — reusing `footer.disclosure` so the two cannot drift. A test asserts the banned types never appear on any page.

## Consequences

- `public/og-image.png` and `public/logo.png` are generated from the committed SVGs by `npm run og:generate` and checked in. Rasterising at build time would put font rendering on the CI host's font stack; committing the output keeps the card deterministic. Regenerate when the SVGs change.
- Google requires an Organization `logo` in PNG, JPG or GIF, which is why `logo.png` exists alongside the SVG favicon.
- `robots.txt` deliberately did **not** disallow the hidden `/prototype/` pages while they existed (deleted 15 Sep 2026). They carried `noindex`, and a `Disallow` would have stopped crawlers reaching the tag that de-indexes them — blocking crawl and blocking indexing are different things. The same reasoning applies to any future noindexed page.
- `Layout.astro` gains a `noindex` prop, used by the 404. Noindexed pages emit no structured data. astro-seo always emits a canonical, so the 404 keeps a self-referencing one; `noindex` takes precedence and it is inert.
- Titles are capped at 60 characters and descriptions at 158, enforced in `tests/e2e/seo.spec.ts` rather than left to review.
- The same generator emits `public/favicon.ico` (16/32/48 PNG-in-ICO) and `public/apple-touch-icon.png` (180×180, flattened full-bleed because iOS backfills transparency with black). Clients and crawlers that request `/favicon.ico` without reading the head were getting a 404.
- `tsconfig.json` maps the `sharp` types explicitly — the package ships declarations its `exports` map does not expose. That mapping is for `astro check` only; `tsx` also honours it at runtime and would import the `.d.ts` as code, so `og:generate` runs under plain `node`, which strips types natively and never reads `tsconfig.json`.

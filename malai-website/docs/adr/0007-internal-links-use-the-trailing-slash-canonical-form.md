# Internal links use the trailing-slash form, matching the canonical and the sitemap

Every page's canonical tag and every sitemap entry names the trailing-slash URL — `https://malaibank.com/security/` — because Astro's default `directory` build format emits `security/index.html`. The nav, footer, section teasers and in-page CTAs all linked the slash-less form, `/security`. The edge URI-rewrite function described in [[ADR-0003]] serves a slash-less path by rewriting to `index.html` **without redirecting**, so both URLs return 200 with identical bodies.

That gave Googlebot two crawlable URLs per page, discovered from the site's own navigation, and it crawled both. Search Console's 2026-08-25 coverage report shows the result: `https://malaibank.com/security` filed under "Alternative page with proper canonical tag", while five of the nine canonical pages sat in "Discovered – currently not indexed". The canonical tag was doing its job — Google picked the right URL — but the site was spending a new domain's small crawl allowance discovering duplicates of pages it had not yet crawled once.

Internal links are therefore authored in the trailing-slash form throughout, and `tests/e2e/seo.spec.ts` asserts that no rendered page links a slash-less internal route.

## Considered options

- **`trailingSlash: "always"` in `astro.config.mjs`.** The obvious lever, and wrong here. It changes only the dev and preview servers, never the deployed artifact — production is S3 objects behind the CloudFront rewrite, which keeps serving both forms regardless. Worse, it makes `astro preview` answer any slash-less path with Astro's own built-in "404: Not Found (trailingSlash is set to \"always\")" page instead of the site's `404.html`, so `tests/e2e/not-found.spec.ts` and every spec navigating a slash-less path fails against a server that no longer resembles production. It would have bought local strictness at the cost of a test suite that lies about the deployed site.
- **A 301 from the slash-less form at the edge.** The complete fix, and the only one that also collapses URLs Google already knows and links other sites may already carry. It belongs in the CloudFront function in `malai-infra`, not here, and it does not remove the need to stop emitting the links in the first place.
- **Author the canonical form and guard it in the suite (chosen).** Fixes what this repo controls: the links Google discovers. Production-accurate, because the guard reads the rendered HTML — the same bytes the crawler parses.

## Consequences

- `breadcrumbLabelFor` in `src/lib/breadcrumbs.ts` already normalised trailing slashes before looking up a label, so the nav and footer hrefs changed without touching breadcrumb structured data.
- The e2e specs navigate the trailing-slash paths, so `INDEXABLE_PAGES` entries are now the canonical URL path verbatim; the `path === "/" ? "/" : \`${path}/\`` fixups that appeared in canonical, breadcrumb and sitemap assertions are gone.
- The guard skips anchors, `mailto:`, and any final segment containing a dot, so asset links such as `/favicon.ico` and `/sitemap-index.xml` do not register as offenders. A new extensionless top-level file in `public/` would, and needs the exemption widening.
- The slash-less URLs stay live and still return 200. Nothing in this repo can redirect them; until `malai-infra` adds the 301 they remain crawlable if an external site links one, and the canonical tag is what keeps them out of the index.
- `public/robots.txt` is unchanged. Disallowing the published Kensa report at `/end-to-end-flows/` was considered and rejected: crawl budget is not a binding constraint at nine pages, Googlebot renders the viewer's JavaScript, and a blocked URL carrying four internal links with descriptive anchor text is precisely what produces "Indexed, though blocked by robots.txt" — a worse state than the status quo, because the block also hides any future `noindex`. If the report should leave the index, the lever is the backend pipeline injecting `<meta name="robots" content="noindex">` beside the `<base>` tag it already injects, per [[ADR-0003]].

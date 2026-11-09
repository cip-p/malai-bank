# No executable inline scripts, so the strict CSP holds

The marketing site is served behind a CloudFront response-headers policy whose `script-src` is `'self' https://plausible.io` with no `'unsafe-inline'`. Astro inlines small bundled `<script>` blocks straight into the HTML, and an inlined script is exactly what that policy blocks. The mobile navigation — the hamburger toggle, its Escape handler and the close-on-link-tap behaviour in `SiteHeader.astro` — was inlined, blocked, and therefore dead on every page of the deployed site. Desktop navigation is CSS-driven and was unaffected, which is why it went unnoticed: the failure only exists on small viewports, only in production, and only where the header is served.

Nothing in the local toolchain serves the CSP, so `npm run test:e2e` exercised a menu that works locally and is inert once deployed. The same blind spot had already swallowed the analytics tracker, whose inline queue stub was moved to `/plausible-init.js` for this reason.

We set `vite.build.assetsInlineLimit: 0` so Astro emits every script as a file under `_astro/`, served from the origin and satisfying `'self'`, and we test the pages against the deployed policy rather than trusting review.

## Considered options

- **Add `'unsafe-inline'` to `script-src`.** One line in `malai-infra` and everything works. It also retires the policy: `'unsafe-inline'` permits any injected inline script, which is the class of attack the header exists to stop. Not worth trading on a site whose pitch is that its security posture withstands review.
- **Hash or nonce the inline block.** Keeps the policy strict and the script inline. Hashes change whenever the script changes, so the CSP in `malai-infra` becomes a build artefact of `malai-website` that must be regenerated and redeployed in lockstep across two repos. Nonces need per-request header generation, which a static site on a CDN cannot do.
- **Hand-write the script into `public/`,** as `plausible-init.js` is. Consistent with what already exists, but it opts that file out of bundling, TypeScript and the component it belongs to — `SiteHeader.astro` would own markup whose behaviour lives in an unrelated directory with no link between them.
- **Disable inline emission at the bundler (chosen).** One config line, no cross-repo coupling, and it applies to every script the site will ever add rather than the one we happened to find. On this site the entire cost is a single extra file in `_astro/`.

## Consequences

- `assetsInlineLimit: 0` also governs asset and stylesheet inlining. Neither changes here: the CSS bundle already exceeded the previous 4kb threshold and shipped as an external `<link>`, and the build produces no `data:` URIs. Measured effect is one additional file. Lighthouse after the change: performance 0.99, accessibility 1.0, best-practices 1.0, SEO 1.0 — all above the gates in `lighthouserc.json`.
- `tests/e2e/content-security-policy.spec.ts` replays the deployed policy by injecting it onto document responses, then asserts no `securitypolicyviolation` fires on any page, that no page ships an executable inline script, and that the structured data and analytics stub both survive. Reverting the config line fails all 14 of its tests, so the guard is known to detect the regression rather than merely accompany it.
- That spec hard-codes the policy from `infra/malai/cloudfront-malai-website/main.tf` in another repo. It cannot import across the repo boundary, so the two can drift: a directive tightened in `malai-infra` will not fail this suite until `PRODUCTION_CSP` is updated to match. Treat the constant as part of the CSP's definition of done.
- JSON-LD is unaffected and stays inline. A `<script type="application/ld+json">` is a data block that the HTML spec never executes, so `script-src` never evaluates it — confirmed by the 404 page, which carries no structured data and violated the policy anyway before this change.

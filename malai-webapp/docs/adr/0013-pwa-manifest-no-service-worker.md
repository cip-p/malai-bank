# PWA manifest and install icons, but no service worker

The webapp ships a `manifest.webmanifest` plus the standard install-icon set (favicon, `apple-touch-icon`, maskable Android icon, splash). It is installable on iOS and Android home screens and surfaces the Chrome install prompt on supported desktops. It **does not** register a service worker, has no offline shell, and has no caching layer beyond the browser's HTTP cache.

Installability earns the demo a real moment — a prospect can "Add to Home Screen" mid-meeting and show the team a phone icon that opens malai full-screen. The service worker, by contrast, would buy us a cached app shell that loads with no network — but every interaction in this app hits the API (balances, transactions, transfers, payees), so the offline shell renders a logged-in-looking skeleton that immediately fails on first read. That is a worse demo than a clean offline message, plus it adds the SW lifecycle, cache-invalidation, and stale-shell-on-deploy problems that PWAs are known for.

Install icons are `malai`-branded only, regardless of the active **Theme** ([ADR-0011](./0011-theme-catalogue-as-fictional-bank-brands.md)). The manifest is fixed at deploy time — Halcyon and Linden are in-app brands, not in-OS brands. The favicon follows the same rule.

## Considered options

- **Manifest + icons, no service worker (accepted).** Installable, online-only, no SW lifecycle. Honest about what the demo is.
- **Full PWA with service worker.** Adds an offline app shell. For a banking app where every screen needs the API, the shell is misleading; cache-invalidation and deploy-staleness are real maintenance cost. Demo lift is marginal.
- **No PWA at all.** Drops the favicon-and-manifest set, keeps only an `index.html` favicon. Loses the "install on my phone" demo moment.

## When this ADR is revisited

If a malai feature ever ships that _should_ work offline (e.g. a cached statements view, an offline-readable transaction log for accessibility reasons), reopen this decision then. Until that feature exists, "offline" has no honest demo to support.

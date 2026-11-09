# PWA install set: manifest + icons + favicon refresh

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The install-icon and manifest set that lets a demo audience add malai to their phone's home screen and launch it full-screen. After this slice lands, a visitor on iOS or Android sees the "Add to Home Screen" affordance and an installed launch experience that reads as a native banking app. No service worker, no offline shell — per [ADR-0013](../../docs/adr/0013-pwa-manifest-no-service-worker.md).

- **`public/manifest.webmanifest`**: name = "malai", short_name = "malai", display = "standalone", start_url = "/", scope = "/", theme_color + background_color drawn from the refreshed malai brand palette, orientation = "portrait-primary".
- **Icon set** (all malai-branded, regardless of which Theme is active in-app):
  - `favicon.svg` — refreshed malai mark (replaces the existing tab favicon reference).
  - `apple-touch-icon-180.png` — 180×180, for iOS home screen.
  - `icon-192.png` — 192×192, standard Android.
  - `icon-512.png` — 512×512, maskable variant for Android adaptive icons. Marked `purpose: "maskable"` in the manifest.
  - A splash image suitable for the iOS launch screen.
- **`index.html`** updated:
  - `<link rel="icon">` → refreshed favicon.svg.
  - `<link rel="apple-touch-icon">` → apple-touch-icon-180.png.
  - `<link rel="manifest">` → `/manifest.webmanifest`.
  - `<meta name="theme-color">` → malai brand colour.
- The manifest and icons are **static for the deployment**. Active in-app Theme does not affect them. Halcyon and Linden are in-app brands, not in-OS brands. The favicon does not change when the user switches Theme inside the app.
- No service worker registered. No offline shell. No caching strategy beyond the browser's HTTP cache.

## Acceptance criteria

- [ ] `public/manifest.webmanifest` exists with the values described above.
- [ ] Five icon assets ship under `public/`: refreshed `favicon.svg`, `apple-touch-icon-180.png`, `icon-192.png`, `icon-512.png` (maskable), splash.
- [ ] `index.html` references favicon, apple-touch-icon, manifest, and theme-color correctly.
- [ ] Chrome / Edge surface the install prompt on the running app (verify manually — record in the issue thread).
- [ ] iOS "Add to Home Screen" produces an icon using `apple-touch-icon-180.png`; launching from the home screen opens malai standalone with the malai mark (verify manually — record in the issue thread).
- [ ] Android maskable icon previewed under the masking tool of choice (e.g. maskable.app); record screenshots in the issue thread.
- [ ] No service worker is registered; `navigator.serviceWorker.getRegistrations()` returns an empty list on the running app.
- [ ] Switching in-app Theme to halcyon or linden does **not** change the `<link rel="icon">` or `<link rel="manifest">`.
- [ ] No comments anywhere.

## Blocked by

- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md) (the favicon and icon palette derive from the refreshed malai brand colours; the manifest's `theme_color` references the same)

# 06 — Architecture section: diagram-first layout + annotations + openapi link

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The third below-the-hero section, addressing the technologist [[Visitor]]'s "is the build sound?" question. The section is diagram-first — the architecture diagram is the centrepiece; surrounding annotations name the load-bearing patterns the diagram embodies; a single outbound link points to the public `openapi.yaml`.

This slice ships the **section structure with a placeholder diagram**. Real diagram artwork is replaced in Issue 09; the placeholder must be valid SVG (or an Astro component that renders SVG) and must show the system shape at a level of detail sufficient for the visitor to read the section as honest rather than empty. A simple boxes-and-arrows abstract is fine: edge (TLS termination), API gateway, identity/auth, three to four abstract domain services (e.g. _Account domain_, _Payments domain_, _Audit domain_), managed database, event bus, managed object store, plus the external rails (FPS, BACS, SWIFT, CoP, external Ledger). Component names in the diagram must be **abstract** — no cloud provider names, no internal service names. The same forbidden-terms enforcement (Issue 02) applies to text rendered inside the diagram.

Annotations alongside the diagram surface the load-bearing patterns:

- Cloud-native, multi-AZ across managed primitives
- Microservices with explicit module fences
- Event-driven where coupling would otherwise hurt
- Idempotency on both synchronous and asynchronous communication
- Three-pillar observability — traces, metrics, structured logs
- Infrastructure-as-code per environment — a fresh environment stands up in minutes
- CI/CD with smoke tests post-deploy

A single outbound link points to the public `openapi.yaml`. The link target depends on a follow-up dependency outside this PRD: the demo backend must publicly serve `openapi.yaml`. Until that lands, the link may resolve to a "see what the demo serves" placeholder page or be hidden behind a feature flag in the content schema. Do not block this slice on that dependency — render the link element with a clearly-marked placeholder href and surface the dependency in a comment in the content collection.

The section mounts under the `#architecture` anchor for `StickyNav` from Issue 03.

Content (annotation text, link label, link target) lives in the `content` module. The diagram artwork lives as a static asset (SVG file or an Astro component) referenced from the section.

Forbidden terms must not appear in annotations, link text, or any text inside the SVG. The brand throughout is _Malai_.

The page-smoke spec extends to assert the section is present, the placeholder diagram renders, each annotation appears, the openapi link element is in the DOM with the expected label.

No client-side JS may be added by this section.

## Acceptance criteria

- [ ] An `ArchitectureSection` Astro component exists and is mounted in `index.astro` under an element with `id="architecture"`.
- [ ] The `content` module gains typed entries for: annotation list, openapi link label and `href` (with a placeholder marker until the backend exposes it publicly).
- [ ] A placeholder architecture diagram ships as static SVG (or an Astro component) showing the abstracted system shape — edge, API gateway, identity, abstract domain services, managed database, event bus, managed object store, external rails — using only abstract component names.
- [ ] No cloud provider name, no internal service name, and no other forbidden term appears in the diagram's text labels.
- [ ] Annotations from the content collection render alongside the diagram.
- [ ] The openapi link element is present with the expected label, marked as a placeholder until the backend serves the file publicly.
- [ ] Page-smoke spec extends to cover section presence, diagram presence, annotation count and content, openapi link presence.
- [ ] No client-side JS hydrated by this section.
- [ ] No forbidden term appears in rendered HTML for this section.
- [ ] Lighthouse performance and accessibility do not regress relative to the post–Issue 01 baseline.

## Blocked by

- `01-walking-skeleton.md` — needs the layout, content module, and page composition.

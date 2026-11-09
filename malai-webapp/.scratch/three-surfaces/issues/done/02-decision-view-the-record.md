# 02 — Decision view: the record

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

The replay read rendered as a record, not a form. Worklist rows become links to a Decision view that presents one Decision's full Entry thread in chain order: the Model Decision — rendered prompt, retrieved-context refs with their content hashes, raw output, verdict, model id/version, prompt-template pin (id, version, hash), latency and cost — followed by every Human Disposition oldest-first with its ruling kind, ruling human, and timestamp. A failed Outcome is a recorded fact: timed-out and errored decisions show the recorded absence and the error detail, not an error state. An edit Disposition shows its stored replacement and the derived line-diff against the raw output (unchanged / removed / added), never a stored diff. The Ruling in force is visible without reading the whole thread. Nothing on the page is editable; no copy says "update", "edit in place", or "save".

The retrieved-context refs and verdict arrive untyped from the contract (backend hand-back pending) — render them defensively: refs as type/id/hash rows where those keys exist, the verdict as formatted structure, neither crashing on unexpected shape. An unknown decision id renders a polite not-found within the Ops shell.

## Acceptance criteria

- [ ] Worklist rows deep-link to the Decision view; the URL is shareable and resolves cold
- [ ] Completed decision shows rendered prompt, context refs with hashes, raw output, and verdict as read-only record sections
- [ ] Timed-out and errored decisions render the recorded absence and error detail as facts, with no output section pretending otherwise
- [ ] Disposition thread renders oldest-first in chain order with ruling kind, human, and timestamp per Entry; Ruling in force is glanceable
- [ ] An edit Disposition shows the full replacement and the derived diff with unchanged/removed/added line treatment
- [ ] Unknown decision id → polite not-found inside the Ops shell, not a crash or blank page
- [ ] Page tests cover each Outcome shape, the thread, the diff rendering, and the defensive untyped-ref rendering
- [ ] Axe a11y spec covers the Decision view in both modes
- [ ] E2E journey grows: worklist → open a seeded decision → the record renders

## Blocked by

- 01-contract-refresh-surface-scaffold-and-worklist

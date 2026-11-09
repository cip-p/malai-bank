# 04 — Gate panel: what may a consumer act on right now?

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

The actionable-output gate as a demo moment on the Decision view. A panel asks the gate on demand — not auto-fetched, so a presenter can rule and then ask, watching the answer flip. A dedicated hook translates the responses into first-class states: **in force** (which ruling authorised the output, via which Disposition Entry, plus the output served verbatim) and **no ruling in force** (the 409 `MDR_OUTPUT_NOT_ACTIONABLE` rendered as a calm displayed fact — the refusal is the feature). Any other failure remains an ordinary error state. Nothing about the gate ever toasts.

The seeded mixed-state worklist makes every state demoable without slice 03: an accepted decision serves its raw output, an edited one serves the stored replacement, rejected and awaiting decisions show the refusal.

## Acceptance criteria

- [ ] The panel fetches only when asked, and re-asking after a new ruling reflects the new answer
- [ ] In-force state shows the authorising ruling kind, the authorising Entry, and the output verbatim
- [ ] The 409 renders as the "no ruling in force" state — no toast, no error styling that reads as breakage; hook tests prove the 200→in-force and 409→no-ruling translations and that other statuses stay errors
- [ ] Page tests cover all three panel states
- [ ] E2E: against seed data, an accepted decision serves output and an unruled one shows the refusal; the expected 409 is allow-listed in the API-failure guard for these specs
- [ ] Axe a11y coverage of the panel states in both modes

## Blocked by

- 02-decision-view-the-record (parallel with 03-ruling-draft)

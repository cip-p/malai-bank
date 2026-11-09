# 09 — CI housekeeping: stale smoke-spec reference

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md` (discovered during the three-surfaces investigation; independent of the feature).

## What to build

The CI script's smoke step invokes a Playwright spec by a filename that no longer exists in the e2e directory, so the smoke invocation targets nothing. Point the smoke step at the spec(s) that actually express the smoke intent today (the signed-in landing round-trip), or at a named smoke subset, and make the choice self-evident so the reference can't silently rot again.

## Acceptance criteria

- [ ] The smoke step invokes only spec files that exist
- [ ] CI passes end-to-end with the corrected step
- [ ] The chosen smoke scope is stated in the script where the filename is used

## Blocked by

None - can start immediately.

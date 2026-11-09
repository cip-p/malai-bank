# 06 — The gate and the replay read: latest-wins actionable output, derived diffs

Status: ready-for-agent

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

The MDR's read surface, where the "no model output actionable without a linked Human Disposition" invariant is enforced. The replay read serves a decision's full Entry thread in chain order — for EDIT Dispositions the diff between Raw output and edited output is derived at read time from the two immutable inputs, never stored. The actionable-output read applies latest-wins: it refuses until the Ruling in force allows, serves the Raw output under accept and the stored edited output verbatim under edit, and refuses under reject or no ruling.

## Acceptance criteria

- [ ] `GET /api/v1/admin/mdr/decisions/{decisionId}` (ADMIN role) returns the full Entry thread in chain order — the 18-month replay read: Rendered prompt, Raw output, verdict, Outcomes, every Disposition, derived diffs for edits
- [ ] `GET /api/v1/admin/mdr/decisions/{decisionId}/actionable-output` returns 409 when no Disposition exists
- [ ] Under an ACCEPT Ruling in force it serves the Raw output; under EDIT it serves the stored edited output verbatim; under REJECT it returns 409
- [ ] Latest wins: accept then reject → 409; reject then accept → serves again — with the ruling history intact in the replay read
- [ ] Response contracts strict-matched with noExtraProps; ADMIN-only enforced; sad path for unknown decision id

## Blocked by

- `05-human-dispositions.md`

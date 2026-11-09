# MDR Entries declare whether a ruling is expected

The reviewer's worklist lands on `AWAITING_RULING`, which was exactly right while every Model Decision parked a payment. The Copilot breaks that: a read-only Explanation holds nothing up, yet every one of them would arrive in the worklist as work, pushing genuine referred payments down the list as the demo gets more interactive. We decided a Model Decision declares **`advisory`** about itself on the write, stored on the Entry, surfaced on the summary row and as a worklist filter, with the reviewer's default view narrowing to non-advisory decisions awaiting a ruling. It is a property of the decision, not of who consumes it, so the MDR stays ignorant of its consumers (ADR-0009), and it forbids nothing: a Human Disposition may still be appended and the Gate answers exactly as before.

## Considered Options

- **Filter by `promptTemplateId` in the Ops UI** — no backend change; the worklist already carries template identity. Rejected: it hardcodes into the webapp which templates constitute work, so every new Decision point needs a UI change to stay out of a reviewer's way, and the record itself would still not know the difference.
- **Leave the worklist mixed** — the most literal rendering of one record. Rejected: the reviewer's landing page becomes a mix of things to act on and things to read, and it degrades further with every Decision point added.
- **A separate advisory chain or table** — perfect separation. Rejected: two chains for one plane destroys the single-record property that ADR-0007 bought.

## Consequences

One nullable column, one field on the write request, one summary field and one query parameter — the response-enum-widening compatibility check will flag the contract change, to be approved consciously as the `REFERRED`/`DECLINED` widening was. Seeded MDR demo data stays valid: absent means not advisory. Chain verification and the Gate are untouched.

**Amended when the Copilot's e2e journey landed:** the replay read (`GET /api/v1/admin/mdr/decisions/{decisionId}`) carries `advisory` on its Model Decision view as well. Replay is the auditor's read of a single Entry; having to call the worklist to learn whether a ruling was ever expected of the Entry in front of you makes the record answer a question about itself only in aggregate. Additive and non-breaking — the value is the same stored declaration the summary already surfaces, and the same `absent means not advisory` reading applies. The Gate still ignores it: advisory forbids nothing.

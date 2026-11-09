# Referred payments move only via the MDR gate

Enforcement of payment-screening rulings needed a consumption mechanism, and we decided `payments-service` learns and enacts rulings exclusively through the MDR's actionable-output gate: a new internal, service-JWT-scoped twin of the admin gate endpoint, polled every few seconds for each `REFERRED` payment (the `decisionId` stored on the payment row as an opaque pointer, never a foreign key). The gate is the MDR's single enforcement surface — consumers never read Human Dispositions directly, so the accept/edit/reject → actionable-output translation lives in exactly one place, and a payment moves only when the gate serves a verdict (`APPROVE` releases, `DECLINE` declines, 409 leaves it parked, per ADR-0008).

## Considered Options

- **Disposition-appended SQS event** — mdr-service publishes, payments consumes and confirms via the gate. Production-shaped at volume, rejected for now: it buys a queue, an envelope contract, and mdr-side publishing machinery to shave seconds a demo cannot perceive. Named in the PRD as the upgrade path; the gate remains the confirmation surface even then.
- **Direct disposition read** — payments interprets the disposition thread itself. Rejected: it duplicates the ruling-in-force logic outside the MDR and lets consumers diverge on what "actionable" means.
- **mdr-service calls payments back on ruling** — rejected: the MDR must stay ignorant of its consumers.

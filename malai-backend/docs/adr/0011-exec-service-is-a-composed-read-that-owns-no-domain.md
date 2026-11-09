# exec-service is a composed read that owns no domain

The Exec dashboard's four numbers live in four bounded contexts — `account_holders`, `ledger`/`payments`, `cards`, and `payments` again — and `noCrossModuleInternalAccess` forbids any service reaching into another's internals. We decided to serve them as one composed read, `GET /api/v1/exec/metrics`, from a new `exec-service` that owns no tables and no domain of its own, fanning out over the existing client pairs to an internal metric read on each owning service. Every metric arrives with explicit availability, so a sick or slow source degrades one tile to the em-dash the frontend already renders honestly rather than failing the screen.

This makes `exec-service` the first module in the repo that is a backend-for-frontend rather than a domain owner — deliberately, because the alternative pushes the composition into the client.

## Considered Options

- **Four endpoints, one per owning context** — zero cross-service calls, zero aggregation, and each tile fails independently for free. Rejected: it scatters four screen-shaped operations across four domains' contracts and makes the dashboard's shape an accident of service topology, against the contract-first rule that the screen's needs are designed before controllers exist.
- **One service querying the four tables directly** — cheapest by far, and the tables all live in `public`. Rejected: it defines JPA entities over other contexts' tables, which `noCrossModuleInternalAccess` forbids in spirit even where it cannot catch it, and V5's "the service boundary is HTTP, not the database" says so explicitly.
- **Fold the aggregation into an existing service** — avoids minting a module. Rejected: whichever service hosted it would gain a fan-out to its peers that has nothing to do with its domain.

## Consequences

The internal metric reads are service-JWT-scoped and non-mutating, so the loopback-audit deadlock documented in `payments-service/CONTEXT.md` does not apply — nothing here writes to the audit chain. Each fan-out leg carries a per-source timeout: "the exec dashboard renders in bounded time, always" is a product promise, and a slow source must degrade to unavailable rather than hang the screen. `exec-service` does own domain language despite owning no tables — Banking day, New customers, Deposits, Payments stopped, and Metric currency are defined in its `CONTEXT.md`.

# Pending and declined card activity composes into the account feed server-side, in account-service

An authorised-uncaptured card purchase has no ledger entry (it is a Hold), and a decline has no balance effect at all — yet both must be visible in the account transaction feed: the pending row is the signature card UX, the decline row is what makes the freeze demo legible, and without them the available balance drops (or a purchase vanishes) unexplained. We keep the webapp's single-feed contract: `account-service`'s transactions endpoint, which already composes over the ledger, also pulls the account's in-flight and declined card authorisations via the new `malai-card-client` and merges them in, flagged `PENDING` / `DECLINED` with merchant names. Settled purchases need no composition — the presentment posting writes `merchant_name` onto the ledger entry directly.

## Considered options

- **Server-side composition in `account-service`** *(accepted)*. One feed endpoint stays the contract; every client (webapp today, mobile tomorrow) gets merge/sort semantics for free; precedent already exists — the feed and statements are compositions over the ledger client.
- **Webapp merges two endpoints client-side.** Keeps `account-service` thin but pushes merge/dedupe into every client and breaks the single-feed contract. Rejected.
- **Card-screen-only activity.** Feed stays pure ledger; pending/declined live on a per-card view. The screen every demo lingers on misses the instant-purchase moment. Rejected.

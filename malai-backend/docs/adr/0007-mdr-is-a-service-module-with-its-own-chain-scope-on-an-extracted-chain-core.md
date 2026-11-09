# The Model Decision Record is a service module with its own hash-chain scope, on a chain core extracted from the audit plane

The MDR — an append-only, tamper-evident record of AI-assisted decisions — needs the chaining guarantees the audit plane already provides, plus a typed schema and query surface of its own. We decided: a new `malai-services/mdr-service` module owns the MDR tables, chained under a second `chain_head` scope row (`'mdr'`), and the chain mechanics (canonical JSON, `SHA-256(prev_hash || payload)`, `SELECT … FOR UPDATE` head advance, batch replay verification) are extracted from `com.malai.security.audit` into a generalised core inside `malai-security` that both chains share. Note the widely-repeated phrase "hash-chained ledger" is a conflation: the ledger has no hash chain — its integrity is double-entry sum-to-zero (`V5`). The only chain is the audit plane's, and the MDR becomes its second tenant.

## Considered Options

- **Ride the existing `audit_events` chain** (MDR entries as audit rows with JSONB payloads). Zero new chain code and the existing verifier covers it for free — but no typed columns or foreign key for disposition links, every model decision would serialise on the same global `chain_head` lock as every business mutation on the platform, and a rich domain concept would move into `malai-security` commons.
- **Duplicate the writer/verifier into the MDR module.** Leaves the proven audit plane untouched and the module fence maximally clean — but forks security-critical hashing code; the canonical-JSON round-trip subtlety (writer hashes the re-parsed form so the verifier matches) is exactly the kind of fix that would silently miss the second copy.

## Consequences

- `chain_head.scope` stops being aspirational — the "v1 has one shard" note in `HashChainedAuditWriter` becomes two shards, and MDR write throughput never contends with business-write auditing.
- The extraction refactors code eight services depend on. The safety net is the existing tamper-test harness (`AuditChainVerifierTest`, `HashChainedAuditWriterTest`); the refactor must leave audit hashes byte-for-byte identical.
- The chain core stays in `malai-security` because the ArchUnit rule `mutatingControllersReachAuditWriter` pins `com.malai.security.audit.AuditWriter` and `malai-security` is a fence-exempt shared root every module may depend on.
- `mdr-service` takes the full service convention set: root pom registration, `@Import` in `MalaiApiApplication`, its own ArchUnit `ArchitectureTest`, a diagram party lane, and a `CONTEXT.md`. No `malai-mdr-client` pair yet — client modules exist only where internal call-outs exist, and nothing calls the MDR in this increment.

package com.malai.security.audit

// Audit-as-port (architectural invariant #3). Every mutating use case takes this
// as a constructor dependency. Plan 02-08's ArchUnit rule fails the build if a
// mutating controller's service collaborator omits it.
//
// Append-only at the application layer; further enforced at the DB layer by V11
// grants -- malai_app has only SELECT, INSERT on audit_events, and UPDATE,
// DELETE,TRUNCATE are revoked. The verifier (AuditChainVerifier) is the
// supplementary defence: any out-of-band tamper that bypasses the role grants
// (a DB superuser, a manual psql session) is detected on the next verify run.
//
// Implementations also (review #2 + #8):
//   * redact sensitive keys (password, pan, cvv, token, ...) from
//     beforeState / afterState before hashing or persisting -- see [AuditRedactor].
//   * round-trip the state maps through Jackson before canonicalisation, so
//     the writer hashes the same JSONB-serialised form the verifier sees on
//     replay (otherwise BigDecimal("1.50") writes "1.50" but reads back as
//     Double 1.5 -- mismatch on a clean chain).
//
// Two methods, two transactional contracts. Pick the one that matches the call
// site's regulatory intent:
//
//   record(event)
//     Propagation.REQUIRED. Joins the caller's @Transactional if one is
//     active; otherwise opens its own short-lived tx and commits the row
//     standalone. When the caller IS transactional the row commits IFF the
//     business write commits -- atomic success-path audit (LOGIN_SUCCEEDED,
//     TRANSFER_EXECUTED, ...). When the caller is NOT transactional the
//     behaviour is equivalent to recordIndependently.
//
//   recordIndependently(event)
//     Propagation.REQUIRES_NEW. Always opens a new tx, suspending any active
//     caller tx for the duration of the audit append and resuming it after.
//     The row commits regardless of caller outcome -- use for failure-path
//     audit that must persist even when the caller throws (REGISTER_FAILED,
//     VERIFICATION_FAILED, FRAUD_FLAGGED, soft-locks, ...).
//
// Footgun: do NOT call recordIndependently AFTER a record() in the same outer
// tx. The outer tx still holds the chain_head FOR UPDATE lock and the inner
// REQUIRES_NEW tx deadlocks waiting for it. Failure-path audits should fire
// before any success-path record() in the same call chain (in practice this
// is automatic -- failure paths are catch blocks; success paths are after).
interface AuditWriter {
    fun record(event: AuditEvent)

    fun recordIndependently(event: AuditEvent)
}

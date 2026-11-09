package com.malai.security.audit

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

// Canonical event payload appended to audit_events by AuditWriter.record. The hash
// chain hashes a deterministic JSON projection of this object (CanonicalJson),
// independent of how Postgres stores the JSONB before/after columns.
//
// Field nullability:
//   - actorId is nullable for ActorType.SYSTEM rows (e.g. LOGIN_FAILED with no
//     resolved user)
//   - resourceType / resourceId are optional for non-resource-bound actions
//   - ts is optional in the call site; HashChainedAuditWriter stamps it with
//     TimeService.now() when null, so the writer is the single source of truth
//     for ordering vs the stubbed clock during tests
//   - traceId mirrors MDC.get("trace_id"); call sites may pass it explicitly to
//     bypass MDC capture (used by tests that pin a deterministic trace id).
data class AuditEvent(
    val actorId: UUID?,
    val actorType: ActorType,
    val action: String,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val beforeState: Map<String, Any?>? = null,
    val afterState: Map<String, Any?>? = null,
    val ip: InetAddress? = null,
    val userAgent: String? = null,
    val requestId: String? = null,
    val traceId: String? = null,
    val ts: Instant? = null,
)

package com.malai.security.audit

import com.malai.security.chain.CanonicalJson as ChainCanonicalJson

// Deterministic canonical JSON for the audit chain's hash payload. The emission
// rules (sorted keys, escape set, null handling, type-specific formatting) live
// in the shared chain core [com.malai.security.chain.CanonicalJson]; this object
// owns only the fixed projection of AuditEvent. Writer and verifier both call
// serialize(event), so the two sides cannot diverge.
//
// Rules (enforced by the chain core, pinned by CanonicalJsonTest's golden snapshot):
//   - Object keys are sorted alphabetically (recursively for nested maps).
//   - Numbers via Number.toString (no scientific notation; Long emits as digits).
//   - Strings UTF-8, JSON-escaped using the standard JSON escape set.
//   - Booleans / null: true / false / null literals.
//   - InetAddress -> hostAddress; UUID -> toString; Instant -> ISO-8601 toString.
//   - Arrays: order preserved (lists are intentionally NOT sorted -- order is
//     part of the audit payload's meaning).
//   - No whitespace anywhere in the output.
//   - Null fields are INCLUDED -- consistent presence is easier to reason about
//     than omitting, and a future schema evolution that adds a field will not
//     silently change historical hashes.
object CanonicalJson {

    fun serialize(event: AuditEvent): String =
        ChainCanonicalJson.serialize(
            mapOf(
                "action" to event.action,
                "actorId" to event.actorId?.toString(),
                "actorType" to event.actorType.name,
                "afterState" to event.afterState,
                "beforeState" to event.beforeState,
                "ip" to event.ip?.hostAddress,
                "requestId" to event.requestId,
                "resourceId" to event.resourceId,
                "resourceType" to event.resourceType,
                "traceId" to event.traceId,
                "ts" to event.ts?.toString(),
                "userAgent" to event.userAgent,
            ),
        )
}

package com.malai.security.audit

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.security.chain.ChainAppender
import com.malai.security.chain.ChainHeads
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.temporal.ChronoUnit

// Postgres-backed AuditWriter (D-2.11, BACK-05). Single linear hash chain on
// scope 'global', serialised on its chain_head row via the shared
// [ChainAppender] (SELECT ... FOR UPDATE lock, SHA-256(prev_hash || payload),
// head advance). INSERT audit_events + UPDATE chain_head happen inside the
// calling thread's @Transactional, so an audit row only commits when the
// surrounding business write commits.
//
// Concurrency: every record() acquires the scope's chain_head row lock for the
// duration of the surrounding transaction. This serialises append throughput on
// the audit chain (D-2.11 / pitfall #6: acceptable for the demo workload).
// Other scopes (e.g. 'mdr') chain independently and never contend with this lock.
@Service
class HashChainedAuditWriter(
    private val jdbc: JdbcTemplate,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
    private val objectMapper: ObjectMapper,
) : AuditWriter {

    private val log = LoggerFactory.getLogger(javaClass)

    private val chainAppender = ChainAppender(ChainHeads(jdbc))

    @Transactional(propagation = Propagation.REQUIRED)
    override fun record(event: AuditEvent) = appendChained(event)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordIndependently(event: AuditEvent) = appendChained(event)

    private fun appendChained(event: AuditEvent) {
        var effective: AuditEvent? = null

        // The payload closure runs after the chain-head lock is acquired. It
        // stamps ts (TimeService.now() respects the StubbedTimeService anchor in
        // tests), captures the trace_id from MDC if the caller did not provide
        // one, redacts sensitive values from beforeState / afterState (review #2),
        // and round-trips the state maps through Jackson so the writer hashes
        // exactly what the verifier will see when it re-parses the JSONB column
        // (review #8). Without the round-trip, BigDecimal("1.50") in a state map
        // hashes as "1.50" on the writer but Jackson re-parses JSONB '1.50' to
        // Double 1.5 on the verifier -- hash mismatch on a clean chain.
        val link = chainAppender.append(
            SCOPE,
            {
                val stamped = event.copy(
                    ts = (event.ts ?: timeService.now()).truncatedTo(ChronoUnit.MICROS),
                    traceId = event.traceId ?: MDC.get("trace_id"),
                    beforeState = roundTripJson(AuditRedactor.redact(event.beforeState)),
                    afterState = roundTripJson(AuditRedactor.redact(event.afterState)),
                )
                effective = stamped
                CanonicalJson.serialize(stamped)
            },
        ) { link ->
            val row = requireNotNull(effective)
            // before/after JSONB columns store ordinary Jackson JSON -- canonical
            // JSON only matters for the hash payload, NOT for the stored columns.
            jdbc.update(
                """
                INSERT INTO audit_events
                    (event_id, ts, actor_id, actor_type, action, resource_type, resource_id,
                     before_state, after_state, ip, user_agent, request_id, trace_id,
                     prev_hash, current_hash, seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::inet, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                uuidGenerator.generate(),
                java.sql.Timestamp.from(row.ts),
                row.actorId,
                row.actorType.name,
                row.action,
                row.resourceType,
                row.resourceId,
                row.beforeState?.let(::toJson),
                row.afterState?.let(::toJson),
                row.ip?.hostAddress,
                row.userAgent,
                row.requestId,
                row.traceId,
                link.prevHash,
                link.currentHash,
                link.seq,
            )
        }

        log.debug("audit chain advanced seq={} action={} actor={}", link.seq, effective?.action, effective?.actorId)
    }

    private fun toJson(map: Map<String, Any?>): String = objectMapper.writeValueAsString(map)

    private fun roundTripJson(map: Map<String, Any?>?): Map<String, Any?>? = map?.let {
        objectMapper.readValue(objectMapper.writeValueAsString(it), MAP_TYPE)
    }

    companion object {
        private const val SCOPE = "global"
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}

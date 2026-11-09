package com.malai.security.audit

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.Collections
import java.util.UUID

const val DEFAULT_MAX_EVENTS = 500

data class AuditEventRecord(
    val eventId: UUID,
    val seq: Long,
    val ts: Instant,
    val actorId: UUID?,
    val actorType: ActorType,
    val action: String,
    val resourceType: String?,
    val resourceId: String?,
    val beforeState: Map<String, Any?>?,
    val afterState: Map<String, Any?>?,
    val traceId: String?,
)

@Service
class AuditReader(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${audit.read.max-events:$DEFAULT_MAX_EVENTS}") private val maxEvents: Int,
) {

    fun eventsFor(
        resourceType: String,
        resourceId: String,
        from: Instant? = null,
        until: Instant? = null,
        limit: Int? = null,
    ): List<AuditEventRecord> {
        require(limit == null || limit > 0) { "limit must be positive when given, was $limit" }

        val window = StringBuilder()
        val arguments = mutableListOf<Any>(resourceType, resourceId)
        from?.let {
            window.append(" AND ts >= ?")
            arguments += Timestamp.from(it)
        }
        until?.let {
            window.append(" AND ts < ?")
            arguments += Timestamp.from(it)
        }
        arguments += (limit ?: maxEvents).coerceAtMost(maxEvents).toLong()

        return jdbc.queryForList(
            "$SELECT_COLUMNS WHERE resource_type = ? AND resource_id = ?$window ORDER BY seq ASC LIMIT ?",
            *arguments.toTypedArray(),
        ).map { it.toRecord() }
    }

    private fun Map<String, Any?>.toRecord() = AuditEventRecord(
        eventId = this["event_id"] as UUID,
        seq = (this["seq"] as Number).toLong(),
        ts = (this["ts"] as Timestamp).toInstant(),
        actorId = this["actor_id"] as UUID?,
        actorType = ActorType.valueOf(this["actor_type"] as String),
        action = this["action"] as String,
        resourceType = this["resource_type"] as String?,
        resourceId = this["resource_id"] as String?,
        beforeState = (this["before_state"] as String?)?.let(::parseStateMap),
        afterState = (this["after_state"] as String?)?.let(::parseStateMap),
        traceId = this["trace_id"] as String?,
    )

    private fun parseStateMap(json: String): Map<String, Any?> =
        Collections.unmodifiableMap(objectMapper.readValue(json, MAP_TYPE))

    private companion object {
        val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}

        const val SELECT_COLUMNS =
            """
            SELECT event_id, seq, ts, actor_id, actor_type, action, resource_type, resource_id,
                   before_state::text AS before_state, after_state::text AS after_state, trace_id
            FROM audit_events
            """
    }
}

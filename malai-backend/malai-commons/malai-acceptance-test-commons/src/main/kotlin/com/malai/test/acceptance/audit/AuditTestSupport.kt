package com.malai.test.acceptance.audit

import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate

interface AuditTestSupport : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun theAuditChainIsAtGenesis() = CallableAction {
        jdbc().update("DELETE FROM audit_events")
        jdbc().update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
        )
    }

    fun theAuditEventsInTheDatabase() = StateCollector { _ ->
        jdbc().queryForList(
            """
            SELECT seq, prev_hash, current_hash, event_id, ts, actor_id, actor_type, action,
                   resource_type, resource_id, before_state::text AS before_state,
                   after_state::text AS after_state, ip::text AS ip, user_agent,
                   request_id, trace_id
            FROM audit_events
            ORDER BY seq ASC
            """.trimIndent(),
        )
    }

    fun theChainHeadInTheDatabase() = StateCollector { _ ->
        jdbc().queryForMap("SELECT scope, last_hash, last_seq FROM chain_head WHERE scope = 'global'")
    }

    fun theLastAuditEventWithAction(action: String) = StateCollector { _ ->
        jdbc().queryForMap(
            "SELECT seq, action, actor_id, actor_type, resource_type, resource_id, " +
                "after_state::text AS after_state " +
                "FROM audit_events WHERE action = ? ORDER BY seq DESC LIMIT 1",
            action,
        )
    }

    fun thereIsAnAuditEventWithAction(action: String) = StateCollector { _ ->
        jdbc().queryForObject(
            "SELECT COUNT(*) > 0 FROM audit_events WHERE action = ?",
            Boolean::class.java,
            action,
        )
    }

    fun theNumberOfAuditEventsWithAction(action: String) = StateCollector { _ ->
        jdbc().queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action = ?",
            Long::class.java,
            action,
        )
    }

    fun anAuditEventIsRecorded(event: AuditEvent) = CallableAction {
        getBean(AuditWriter::class.java).record(event)
    }

    fun theCurrentHashOfAuditEventAtSeqIsTamperedTo(seq: Long, badHash: ByteArray) = CallableAction {
        jdbc().update("UPDATE audit_events SET current_hash = ? WHERE seq = ?", badHash, seq)
    }
}

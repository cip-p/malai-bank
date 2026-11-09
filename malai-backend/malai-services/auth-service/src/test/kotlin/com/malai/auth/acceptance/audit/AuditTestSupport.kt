package com.malai.auth.acceptance.audit

import com.malai.security.audit.AuditEvent
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate

// Per-test reset for the audit chain. The shared @BeforeAll cleanup truncates
// audit_events but intentionally leaves chain_head alone (the V6 genesis row
// must persist). Tests within a class share state, so each audit test that
// asserts on absolute seq=1 / specific hashes must reset both tables explicitly.
//
// Genesis state: chain_head = ('global', 32 zero bytes, 0); audit_events empty.
interface AuditTestSupport : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun theAuditChainIsAtGenesis() = CallableAction {
        jdbc().update("DELETE FROM audit_events")
        // The V6 genesis row may already be present from migration; UPSERT is the
        // simplest way to guarantee post-condition without caring about prior state.
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

    /** Tampers with a row's current_hash to a known-bad value. Used to prove the verifier detects mismatches. */
    fun theCurrentHashOfAuditEventAtSeqIsTamperedTo(seq: Long, badHash: ByteArray) = CallableAction {
        jdbc().update(
            "UPDATE audit_events SET current_hash = ? WHERE seq = ?",
            badHash, seq,
        )
    }

    /** Helper to read the most recent row for a particular action -- used by AuthService wiring tests. */
    fun theLastAuditEventWithAction(action: String) = StateCollector { _ ->
        jdbc().queryForMap(
            "SELECT seq, action, actor_id, actor_type, after_state::text AS after_state " +
                "FROM audit_events WHERE action = ? ORDER BY seq DESC LIMIT 1",
            action,
        )
    }

    /** Helper used by AuthAcceptanceTest-based tests when they want to call record() directly. */
    fun anAuditEventIsRecorded(event: AuditEvent) = CallableAction {
        getBean(com.malai.security.audit.AuditWriter::class.java).record(event)
    }

    /** Bulk-record N SYSTEM audit events. Used by tests that need to exceed a verifier batch size. */
    fun nAuditEventsAreRecorded(count: Int) = CallableAction {
        val writer = getBean(com.malai.security.audit.AuditWriter::class.java)
        repeat(count) { i ->
            writer.record(AuditEvent(
                actorId = null,
                actorType = com.malai.security.audit.ActorType.SYSTEM,
                action = "BULK-${i + 1}",
            ))
        }
    }

    /** Simulates another malai-api instance holding the named ShedLock. */
    fun theShedLockIsHeldByAnotherInstance(name: String) = CallableAction {
        jdbc().update(
            """
            INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
            VALUES (?, now() + INTERVAL '1 hour', now(), 'test-other-instance')
            ON CONFLICT (name) DO UPDATE
                SET lock_until = excluded.lock_until,
                    locked_at = excluded.locked_at,
                    locked_by = excluded.locked_by
            """.trimIndent(),
            name,
        )
    }

    fun theShedLockIsReleased(name: String) = CallableAction {
        jdbc().update("DELETE FROM shedlock WHERE name = ?", name)
    }
}

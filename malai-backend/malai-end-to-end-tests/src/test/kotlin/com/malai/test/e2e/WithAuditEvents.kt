package com.malai.test.e2e

import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import dev.kensa.StateCollector
import java.util.UUID

// E2E read-only access to audit_events plus a chain-reset action. The container's
// boot-time DatabaseCleanup truncates audit_events and re-seeds chain_head's V6
// genesis row, but the container boots once per session -- audit_events accumulates
// across e2e test classes. Tests that assert on absolute seq values (1, 2, 3, ...)
// must call [theAuditChainIsAtGenesis] at the start; tests that only care about
// action sequence can rely on the `@BeforeAll cleanTables()` in MalaiE2ETest which
// truncates audit_events between classes (chain_head is intentionally excluded
// from that list -- it's infrastructure, not data).
//
// Mixed into [MalaiE2ETest]; uses [MalaiE2ETestContext.DATABASE_URL] (docker-compose
// Postgres, malai_test schema).
interface WithAuditEvents {

    fun theAuditEventsInTheDatabase() = StateCollector { _ ->
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT seq, action, actor_type, actor_id,
                       resource_type, resource_id,
                       after_state::text AS after_state, ts
                FROM audit_events
                ORDER BY seq ASC
                """.trimIndent()
            )
            val out = mutableListOf<Map<String, Any?>>()
            while (rs.next()) {
                out += mapOf(
                    "seq" to rs.getLong("seq"),
                    "action" to rs.getString("action"),
                    "actor_type" to rs.getString("actor_type"),
                    "actor_id" to rs.getObject("actor_id"),
                    "resource_type" to rs.getString("resource_type"),
                    "resource_id" to rs.getString("resource_id"),
                    "after_state" to rs.getString("after_state"),
                    "ts" to rs.getTimestamp("ts"),
                )
            }
            out.toList()
        }
    }

    fun theAuditChainIsAtGenesis() = CallableAction {
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.createStatement().execute("DELETE FROM audit_events")
            conn.createStatement().execute(
                """
                INSERT INTO chain_head (scope, last_hash, last_seq)
                VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
                ON CONFLICT (scope) DO UPDATE
                    SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
                """.trimIndent()
            )
        }
    }
}

// SEED_* rows are plumbing the registration seeder writes for the demo dashboard — a ledger
// SEED_TRANSACTION per demo transaction, plus a SEED_PAYEE_CREATED / SEED_STANDING_ORDER_CREATED /
// SEED_DIRECT_DEBIT_CREATED per seeded instrument — not user-driven actions. Drop them all and
// renumber so tests can keep asserting on dense 1..N sequences. `malai-registration-seeder` is
// bundled into malai-api, so these rows now appear after every registration verification; before
// bundling, the seeder lived outside auth-service and these rows were absent from the e2e DB.
fun List<Map<String, Any?>>.mapAuditEvents(): List<AuditEventInDb> = this
    .filterNot { it["action"]?.toString().orEmpty().startsWith("SEED_") }
    .mapIndexed { index, row ->
        AuditEventInDb(
            seq = (index + 1).toLong(),
            actorId = row["actor_id"]?.let { v -> UUID.fromString(v.toString()) },
            actorType = row["actor_type"].toString(),
            action = row["action"].toString(),
        )
    }

data class AuditEventInDb(
    val seq: Long,
    val actorId: UUID?,
    val actorType: String,
    val action: String,
)
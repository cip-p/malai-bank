package com.malai.test.acceptance.kensa.fixtures

import java.sql.Connection
import kotlin.system.measureTimeMillis

// chain_head is intentionally NOT truncated -- the V6 migration inserts the
// single global genesis row ('global', 32 zero bytes, 0) and HashChainedAuditWriter
// requires it to be present. Audit tests reset its state explicitly via the
// AuditTestSupport helper instead.
private val tables = listOf(
    "account_holder_addresses",
    "account_holders",
    "account_identifiers",
    "accounts",
    "aml_cases",
    "audit_events",
    "business_addresses",
    "businesses",
    "card_authorisations",
    "card_refunds",
    "cards",
    "category_overrides",
    "direct_debits",
    "idempotency_keys",
    "individuals",
    "ledger_accounts",
    "ledger_entries",
    "payees",
    "payments",
    "profile_images",
    "settle_up_profiles",
    "sole_traders",
    "sqs_message_dedup",
    "standing_orders",
    "transactions",
    "users",
)

fun Connection.deleteAllFixtures() {
    println("Clearing tables: $tables")

    val elapsed = measureTimeMillis {
        autoCommit = false

        createStatement().use { statement ->
            statement.execute("SET LOCAL session_replication_role = 'replica'")
            statement.execute(tables.joinToString(" ") { "DELETE FROM $it;" })
        }

        commit()
    }

    println("Clearing finished, it took $elapsed ms")
}

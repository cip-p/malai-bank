package com.malai.test.e2e

import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import dev.kensa.StateCollector
import java.time.Instant
import java.util.UUID

/**
 * E2E direct-database helpers for seeding starting balances onto customer accounts and reading
 * back posted ledger entries. The E2E harness has no in-process Spring context, so balance
 * priming and ledger-state assertions go through JDBC against the docker-compose Postgres at
 * [MalaiE2ETestContext.DATABASE_URL] — mirrors [WithAuditEvents]'s direct-DB approach.
 *
 * Seeding a starting balance creates a synthetic SUSPENSE-typed funding account on a random
 * holder (invisible to the customer) and a balanced ledger pair: `-amountMinorUnits` on the
 * funding account, `+amountMinorUnits` on the customer's account. Both rows share the same
 * `transaction_id` so the V5 sum-to-zero deferred trigger passes.
 */
interface WithFunding {

    fun theFollowingBalanceIsSeededOnAccount(
        accountId: UUID,
        amountMinorUnits: Long,
        postedAt: Instant = Instant.parse("2026-05-22T08:00:00Z"),
    ) = CallableAction {
        val fundingAccountId = UUID.randomUUID()
        val fundingHolderId = UUID.randomUUID()
        val fundingUserId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()

        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.autoCommit = false

            conn.prepareStatement(
                """
                INSERT INTO accounts (account_id, user_id, account_holder_id, account_type,
                                      default_category, currency, created_at, name, status)
                VALUES (?, ?, ?, 'SUSPENSE', ?, 'GBP', ?, 'E2E Funding', 'ACTIVE')
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, fundingAccountId)
                ps.setObject(2, fundingUserId)
                ps.setObject(3, fundingHolderId)
                ps.setObject(4, UUID.randomUUID())
                ps.setTimestamp(5, java.sql.Timestamp.from(postedAt))
                ps.executeUpdate()
            }

            conn.prepareStatement(
                """
                INSERT INTO transactions (transaction_id, initiated_by_id, initiated_at, type)
                VALUES (?, ?, ?, 'FIXTURE')
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, transactionId)
                ps.setObject(2, fundingUserId)
                ps.setTimestamp(3, java.sql.Timestamp.from(postedAt))
                ps.executeUpdate()
            }

            conn.prepareStatement(
                """
                INSERT INTO ledger_entries (entry_id, transaction_id, account_id,
                                            amount_minor_units, posted_at)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setObject(2, transactionId)
                ps.setObject(3, fundingAccountId)
                ps.setLong(4, -amountMinorUnits)
                ps.setTimestamp(5, java.sql.Timestamp.from(postedAt))
                ps.setObject(6, UUID.randomUUID())
                ps.setObject(7, transactionId)
                ps.setObject(8, accountId)
                ps.setLong(9, amountMinorUnits)
                ps.setTimestamp(10, java.sql.Timestamp.from(postedAt))
                ps.executeUpdate()
            }

            conn.commit()
        }
    }

    fun theLedgerEntriesForAccountInTheDatabase(accountId: UUID) = StateCollector { _ ->
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.prepareStatement(
                """
                SELECT entry_id, transaction_id, account_id, amount_minor_units, posted_at
                FROM ledger_entries
                WHERE account_id = ?
                ORDER BY posted_at ASC, entry_id ASC
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, accountId)
                val rs = ps.executeQuery()
                val out = mutableListOf<LedgerEntryInDb>()
                while (rs.next()) {
                    out += LedgerEntryInDb(
                        entryId = UUID.fromString(rs.getString("entry_id")),
                        transactionId = UUID.fromString(rs.getString("transaction_id")),
                        accountId = UUID.fromString(rs.getString("account_id")),
                        amountMinorUnits = rs.getLong("amount_minor_units"),
                        postedAt = rs.getTimestamp("posted_at").toInstant(),
                    )
                }
                out.toList()
            }
        }
    }

    /**
     * Reads back the ledger Holds placed on an account. Outbound payments reserve funds with a
     * native Hold at initiation, so this is how a test observes whether the customer's money is
     * still reserved (`PLACED`), given back (`RELEASED`) or spent (`SETTLED`). `holds` is not in
     * [com.malai.test.acceptance.kensa.fixtures.deleteAllFixtures]'s truncate list, so read by
     * the account the test just onboarded rather than expecting a globally empty table.
     */
    fun theHoldsForAccountInTheDatabase(accountId: UUID) = StateCollector { _ ->
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.prepareStatement(
                """
                SELECT hold_id, account_id, amount_minor_units, status
                FROM holds
                WHERE account_id = ?
                ORDER BY placed_at ASC, hold_id ASC
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, accountId)
                val rs = ps.executeQuery()
                val out = mutableListOf<HoldInDb>()
                while (rs.next()) {
                    out += HoldInDb(
                        holdId = UUID.fromString(rs.getString("hold_id")),
                        accountId = UUID.fromString(rs.getString("account_id")),
                        amountMinorUnits = rs.getLong("amount_minor_units"),
                        status = rs.getString("status"),
                    )
                }
                out.toList()
            }
        }
    }

    /**
     * Registers the bank's `SYSTEM` ledger accounts (scheme-settlement + FPS/BACS inbound suspense)
     * in `ledger_accounts` as `ALLOW_NEGATIVE` so the ledger lets them carry the bank's
     * position without tripping the funds guard. The seed (V14) registers them, but the class-level
     * `@BeforeAll cleanTables()` truncates `ledger_accounts`, so any test that drives an
     * outbound settle (`customer -> SCHEME_SETTLEMENT`) or inbound credit (`inbound-suspense ->
     * beneficiary`) must put them back — posting to an unregistered ledger account is a 4xx.
     *
     * Customer accounts register themselves at onboarding (account-service -> ledger), so only the
     * system accounts need re-seeding here. The retired FPS/SWIFT *outbound* suspense accounts are
     * gone: outbound payments now reserve funds with a native ledger Hold and settle straight to
     * SCHEME_SETTLEMENT. `ON CONFLICT DO NOTHING` keeps it idempotent across tests in a class.
     */
    fun theLedgerSystemAccountsAreRegistered() = CallableAction {
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(
                """
                INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy)
                VALUES (?, 'GBP', 'SYSTEM', 'ALLOW_NEGATIVE')
                ON CONFLICT (account_id) DO NOTHING
                """.trimIndent()
            ).use { ps ->
                LEDGER_SYSTEM_ACCOUNTS.forEach { accountId ->
                    ps.setObject(1, accountId)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        }
    }

    companion object {
        val SCHEME_SETTLEMENT_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010002")
        val FPS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010003")
        val BACS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010005")

        private val LEDGER_SYSTEM_ACCOUNTS: List<UUID> = listOf(
            SCHEME_SETTLEMENT_ACCOUNT_ID,
            FPS_INBOUND_SUSPENSE_ACCOUNT_ID,
            BACS_INBOUND_SUSPENSE_ACCOUNT_ID,
        )
    }
}

data class HoldInDb(
    val holdId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
    val status: String,
)

data class LedgerEntryInDb(
    val entryId: UUID,
    val transactionId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
    val postedAt: Instant,
)

package com.malai.test.e2e

import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * E2E seeding helper for the spending-insights feature. The E2E harness has no in-process Spring
 * context, so — like [WithFunding] — it primes the ledger directly through JDBC against the
 * docker-compose Postgres at [MalaiE2ETestContext.DATABASE_URL].
 *
 * Each seeded spend becomes a self-balancing ledger transaction: the customer's account carries the
 * signed amount (with the narrative that drives categorisation — `Categoriser` keyword-matches on
 * `merchant_name` then `description`), balanced by an equal-and-opposite entry on a throwaway funding
 * account so the V5 sum-to-zero deferred trigger passes. Entries are stamped mid-`month` so a
 * `GET /spending?month=…` for that month picks them all up; each gets a distinct `posted_at` a second
 * apart, in list order, so the transaction feed's newest-first ordering is deterministic.
 *
 * Categorisation runs in the container against the real `Categoriser`, so the seeded narratives must
 * match its keyword rules (e.g. "Tesco" → GROCERIES, "Octopus Energy" → BILLS, "rent" → HOUSING); a
 * positive amount is always INCOME.
 */
interface WithInsights {

    fun theFollowingSpendIsSeededOnAccount(
        accountId: UUID,
        month: String,
        entries: List<SeededSpend>,
        transactionType: String = "PAYMENT_SETTLEMENT",
    ) = CallableAction {
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.autoCommit = false
            val monthAnchor = Instant.parse("$month-15T10:00:00Z")

            entries.forEachIndexed { index, spend ->
                val transactionId = UUID.randomUUID()
                val postedAt = monthAnchor.plusSeconds(index.toLong())

                conn.prepareStatement(
                    """
                    INSERT INTO transactions (transaction_id, initiated_by_id, initiated_at, type)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent()
                ).use { ps ->
                    ps.setObject(1, transactionId)
                    ps.setObject(2, UUID.randomUUID())
                    ps.setTimestamp(3, Timestamp.from(postedAt))
                    ps.setString(4, transactionType)
                    ps.executeUpdate()
                }

                conn.prepareStatement(
                    """
                    INSERT INTO ledger_entries (entry_id, transaction_id, account_id,
                                                amount_minor_units, posted_at, merchant_name, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { ps ->
                    ps.setObject(1, spend.entryId)
                    ps.setObject(2, transactionId)
                    ps.setObject(3, accountId)
                    ps.setLong(4, spend.amountMinorUnits)
                    ps.setTimestamp(5, Timestamp.from(postedAt))
                    ps.setString(6, spend.merchantName)
                    ps.setString(7, spend.description)
                    ps.setObject(8, UUID.randomUUID())
                    ps.setObject(9, transactionId)
                    ps.setObject(10, UUID.randomUUID())
                    ps.setLong(11, -spend.amountMinorUnits)
                    ps.setTimestamp(12, Timestamp.from(postedAt))
                    ps.setString(13, null)
                    ps.setString(14, null)
                    ps.executeUpdate()
                }
            }

            conn.commit()
        }
    }
}

data class SeededSpend(
    val entryId: UUID,
    val amountMinorUnits: Long,
    val description: String,
    val merchantName: String? = null,
)

package com.malai.payee.support

import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The payee-service test module cannot import `StandingOrderEntity` (owned by
 * standing-order-service, blocked by ArchUnit), so the deletion-block fixtures seed
 * the `accounts` FK row and the `standing_orders` row via raw `JdbcTemplate` — the
 * mirror of `WithStandingOrders.aPayeeExists` reaching into `payees`.
 */
interface WithStandingOrdersRawSql : WithApplicationContext {

    fun anAccountAndStandingOrderExistForPayee(
        accountHolderId: UUID,
        standingOrderId: UUID,
        payeeId: UUID,
        accountId: UUID = UUID.randomUUID(),
        status: String = "ACTIVE",
        cancelledAt: Instant? = null,
        startDate: LocalDate = LocalDate.parse("2026-06-01"),
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        val jdbc = getBean(JdbcTemplate::class.java)
        jdbc.update(
            """
            INSERT INTO accounts (account_id, account_holder_id, user_id, account_type,
                                  default_category, currency, created_at, name, status)
            VALUES (?, ?, ?, 'PRIMARY', ?, 'GBP', ?, 'Personal', 'ACTIVE')
            """.trimIndent(),
            accountId,
            accountHolderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Timestamp.from(createdAt),
        )
        jdbc.update(
            """
            INSERT INTO standing_orders (standing_order_id, account_id, payee_id, payee_name,
                                         payee_sort_code, payee_account_number, reference,
                                         amount_minor_units, currency, frequency, status,
                                         cancelled_at, start_date, next_payment_at, created_at)
            VALUES (?, ?, ?, 'Landlord', '20-00-00', '12345678', NULL,
                    95000, 'GBP', 'MONTHLY', ?, CAST(? AS TIMESTAMPTZ), ?, ?, ?)
            """.trimIndent(),
            standingOrderId,
            accountId,
            payeeId,
            status,
            cancelledAt?.let { Timestamp.from(it) },
            Date.valueOf(startDate),
            Timestamp.from(Instant.parse("2026-06-01T08:00:00Z")),
            Timestamp.from(createdAt),
        )
    }

    fun theStandingOrderRowInTheDatabase(standingOrderId: UUID) = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForMap(
            "SELECT standing_order_id, payee_id, status FROM standing_orders WHERE standing_order_id = ?",
            standingOrderId,
        )
    }
}

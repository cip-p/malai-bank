package com.malai.standingorder.support

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.standingorder.repository.StandingOrdersRepository
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.audit.AuditTestSupport
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Domain fixtures for standing-order-service.
 *
 * D-2.22: `StandingOrdersRepository.findAllByAccountHolderId` JOINs `standing_orders`
 * to `accounts` via shared SQL (the Kotlin layer cannot import `AccountEntity`).
 * This trait seeds the `accounts` row via raw `JdbcTemplate.update(...)` so the
 * cross-table ownership filter resolves at test time.
 */
interface WithStandingOrders : WithApplicationContext, AuditTestSupport {

    fun standingOrdersRepository(): StandingOrdersRepository = getBean(StandingOrdersRepository::class.java)

    fun anAccountExists(
        accountHolderId: UUID,
        accountId: UUID,
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        insertAccount(accountHolderId, accountId, createdAt)
    }

    fun aPayeeExists(
        payeeId: UUID,
        accountHolderId: UUID,
        name: String = "Landlord",
        sortCode: String = "20-00-00",
        accountNumber: String = "12345678",
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        getBean(JdbcTemplate::class.java).update(
            """
            INSERT INTO payees (payee_id, account_holder_id, name, sort_code, account_number,
                                nickname, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """.trimIndent(),
            payeeId,
            accountHolderId,
            name,
            sortCode,
            accountNumber,
            Timestamp.from(createdAt),
            UUID.randomUUID(),
        )
    }

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    fun anInFlightIdempotencyKeyExists(key: String) = CallableAction {
        getBean(JdbcTemplate::class.java).update(
            """
            INSERT INTO idempotency_keys (service, key, request_hash, status, created_at, expires_at)
            VALUES ('standing-order-service', ?, ?, 'IN_FLIGHT', now(), now() + interval '24 hours')
            """.trimIndent(),
            key,
            ByteArray(32),
        )
    }

    private fun insertAccount(accountHolderId: UUID, accountId: UUID, createdAt: Instant) {
        getBean(JdbcTemplate::class.java).update(
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
    }

    fun anAccountAndStandingOrderExist(
        accountHolderId: UUID,
        standingOrderId: UUID,
        accountId: UUID = UUID.randomUUID(),
        payeeId: UUID? = null,
        payeeName: String = "Landlord",
        payeeSortCode: String = "20-00-00",
        payeeAccountNumber: String = "12345678",
        reference: String? = null,
        amountMinorUnits: Long = 95000,
        currency: Currency = Currency.GBP,
        frequency: Frequency = Frequency.MONTHLY,
        status: InstrumentStatus = InstrumentStatus.ACTIVE,
        startDate: LocalDate = LocalDate.parse("2026-06-01"),
        cancelledAt: Instant? = null,
        nextPaymentAt: Instant? = Instant.parse("2026-06-01T08:00:00Z"),
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        insertAccount(accountHolderId, accountId, createdAt)
        standingOrdersRepository().save(
            StandingOrderEntity(
                standingOrderId = standingOrderId,
                accountId = accountId,
                payeeId = payeeId,
                payeeName = payeeName,
                payeeSortCode = payeeSortCode,
                payeeAccountNumber = payeeAccountNumber,
                reference = reference,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                frequency = frequency,
                status = status,
                startDate = startDate,
                cancelledAt = cancelledAt,
                nextPaymentAt = nextPaymentAt,
                createdAt = createdAt,
            )
        )
    }

    fun theStandingOrderNextPaymentIsResetTo(standingOrderId: UUID, nextPaymentAt: Instant) = CallableAction {
        val existing = standingOrdersRepository().findById(standingOrderId).orElseThrow()
        standingOrdersRepository().save(existing.copy(nextPaymentAt = nextPaymentAt))
    }

    fun theStandingOrdersInTheDatabaseFor(accountHolderId: UUID) = StateCollector { _ ->
        standingOrdersRepository().findAllByAccountHolderId(accountHolderId)
    }

    fun theStandingOrderInTheDatabase(standingOrderId: UUID) = StateCollector { _ ->
        standingOrdersRepository().findById(standingOrderId).orElse(null)
    }
}

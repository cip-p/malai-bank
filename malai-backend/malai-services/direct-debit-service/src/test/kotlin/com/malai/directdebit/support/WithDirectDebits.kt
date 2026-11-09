package com.malai.directdebit.support

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.repository.DirectDebitsRepository
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
 * Domain fixtures for direct-debit-service.
 *
 * D-2.22: `DirectDebitsRepository.findAllByAccountHolderId` JOINs `direct_debits`
 * to `accounts` via shared SQL. The Kotlin layer cannot import `AccountEntity`
 * (Plan 02-08 ArchUnit `noCrossModuleInternalAccess` blocks cross-module access),
 * so this trait seeds the `accounts` row via raw `JdbcTemplate.update(...)`. The
 * schema is shared via `malai-sql` Flyway migrations applied at test boot.
 */
interface WithDirectDebits : WithApplicationContext, AuditTestSupport {

    fun directDebitsRepository(): DirectDebitsRepository = getBean(DirectDebitsRepository::class.java)

    /**
     * Seeds just an `accounts` row — the `direct_debits.account_id` FK target for a mandate lodged
     * through the internal endpoint (where no `direct_debits` row is pre-seeded). Same raw-SQL
     * shape as [anAccountAndDirectDebitExist]'s account insert.
     */
    fun anAccountExists(
        accountHolderId: UUID,
        accountId: UUID,
        name: String = "Alice Walker",
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        getBean(JdbcTemplate::class.java).update(
            """
            INSERT INTO accounts (account_id, account_holder_id, user_id, account_type,
                                  default_category, currency, created_at, name, status)
            VALUES (?, ?, ?, 'PRIMARY', ?, 'GBP', ?, ?, 'ACTIVE')
            """.trimIndent(),
            accountId,
            accountHolderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Timestamp.from(createdAt),
            name,
        )
    }

    /**
     * Seeds an `accounts` row (+ its `SORT_CODE` `account_identifiers` row) and a `direct_debits`
     * row pointing at it. Both share `accountHolderId` so the cross-table SQL ownership filter
     * resolves; the identifier row lets the collection requester's `findDebtorSnapshot` resolve the
     * debtor's rail details. `debtorAccountNumber` defaults to a value derived from `accountId` so
     * seeding several accounts in one test never collides on the identifier uniqueness constraint.
     */
    fun anAccountAndDirectDebitExist(
        accountHolderId: UUID,
        directDebitId: UUID,
        accountId: UUID = UUID.randomUUID(),
        debtorName: String = "Alice Walker",
        debtorSortCode: String = "65-82-37",
        debtorAccountNumber: String = accountId.toString().filter(Char::isDigit).padEnd(8, '0').take(8),
        creditorName: String = "British Gas",
        creditorSortCode: String = "20-00-00",
        creditorAccountNumber: String = "12345678",
        reference: String? = null,
        amountMinorUnits: Long? = 4500,
        currency: Currency = Currency.GBP,
        frequency: Frequency = Frequency.MONTHLY,
        status: InstrumentStatus = InstrumentStatus.ACTIVE,
        startDate: LocalDate = LocalDate.parse("2026-06-01"),
        cancelledAt: Instant? = null,
        nextCollectionAt: Instant? = Instant.parse("2026-06-01T08:00:00Z"),
        createdAt: Instant = Instant.parse("2026-04-01T09:00:00Z"),
    ) = CallableAction {
        val jdbc = getBean(JdbcTemplate::class.java)
        // PRIMARY account row — minimal columns required by V1 schema. accountType and other
        // not-null columns get safe defaults; `name` is the debtor's payment display name.
        jdbc.update(
            """
            INSERT INTO accounts (account_id, account_holder_id, user_id, account_type,
                                  default_category, currency, created_at, name, status)
            VALUES (?, ?, ?, 'PRIMARY', ?, 'GBP', ?, ?, 'ACTIVE')
            """.trimIndent(),
            accountId,
            accountHolderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Timestamp.from(createdAt),
            debtorName,
        )
        jdbc.update(
            """
            INSERT INTO account_identifiers (identifier_id, account_id, identifier_type,
                                             bank_identifier, account_identifier)
            VALUES (?, ?, 'SORT_CODE', ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            accountId,
            debtorSortCode,
            debtorAccountNumber,
        )
        directDebitsRepository().save(
            DirectDebitEntity(
                directDebitId = directDebitId,
                accountId = accountId,
                creditorName = creditorName,
                creditorSortCode = creditorSortCode,
                creditorAccountNumber = creditorAccountNumber,
                reference = reference,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                frequency = frequency,
                status = status,
                startDate = startDate,
                cancelledAt = cancelledAt,
                nextCollectionAt = nextCollectionAt,
                createdAt = createdAt,
            )
        )
    }

    fun theDirectDebitNextCollectionIsResetTo(directDebitId: UUID, nextCollectionAt: Instant) = CallableAction {
        val existing = directDebitsRepository().findById(directDebitId).orElseThrow()
        directDebitsRepository().save(existing.copy(nextCollectionAt = nextCollectionAt))
    }

    fun theDirectDebitsInTheDatabaseFor(accountHolderId: UUID) = StateCollector { _ ->
        directDebitsRepository().findAllByAccountHolderId(accountHolderId)
    }

    fun theDirectDebitInTheDatabase(directDebitId: UUID) = StateCollector { _ ->
        directDebitsRepository().findById(directDebitId).orElse(null)
    }
}

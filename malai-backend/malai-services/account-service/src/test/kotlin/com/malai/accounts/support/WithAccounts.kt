package com.malai.accounts.support

import com.malai.accounts.entity.*
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.*

/**
 * Domain fixture interface for the account-service. Mixed into [com.malai.accounts.acceptance.AccountsAcceptanceTest]
 * (and, via the shared test-jar, into payments-service and malai-api tests) so every test inherits both:
 *
 * - `aXxxExists(...)` builders that persist domain entities directly via the running app's
 *   repository beans (resolved via [WithApplicationContext.getBean]).
 * - `theXxxInTheDatabase[Has|...](...)` collectors used in `then`/`and` blocks to assert on
 *   persisted state.
 *
 * The ledger (transactions, ledger_entries, holds, balance projections) is no longer
 * account-service's — its balance/transactions/statement reads compose over the ledger client and
 * are tested through the [com.malai.client.malai.ledger.WithLedger] WireMock stubs. The
 * [aBalancedLedgerEntryPair] / [theLedgerEntriesInTheDatabase] helpers remain only to seed and read
 * ledger tables directly (via raw SQL — they share the DB; the service boundary is HTTP) for the
 * full-stack tests (payments-service, malai-api) where the real bundled ledger-service reads them.
 */
interface WithAccounts : WithApplicationContext {

    fun accountRepository(): AccountRepository = getBean(AccountRepository::class.java)
    fun accountIdentifierRepository(): AccountIdentifierRepository = getBean(AccountIdentifierRepository::class.java)

    // -------- builders --------

    fun anAccountExists(
        accountId: UUID,
        accountHolderId: UUID,
        accountType: AccountType = AccountType.PRIMARY,
        userId: UUID = UUID.randomUUID(),
        currency: Currency = Currency.GBP,
        defaultCategory: UUID = UUID.randomUUID(),
        createdAt: Instant = Instant.parse("2025-10-15T10:00:00Z"),
        name: String = "Personal",
        status: OnboardingStatus = OnboardingStatus.ACTIVE,
    ) = CallableAction {
        accountRepository().save(
            AccountEntity(
                accountId = accountId,
                accountType = accountType,
                defaultCategory = defaultCategory,
                currency = currency,
                createdAt = createdAt,
                name = name,
                userId = userId,
                accountHolderId = accountHolderId,
                status = status,
            )
        )
    }

    fun anIdentifierExistsForAccount(
        accountId: UUID,
        identifierType: IdentifierType,
        bankIdentifier: String,
        accountIdentifier: String,
        identifierId: UUID = UUID.randomUUID(),
    ) = CallableAction {
        accountIdentifierRepository().save(
            AccountIdentifierEntity(
                identifierId = identifierId,
                accountId = accountId,
                identifierType = identifierType,
                bankIdentifier = bankIdentifier,
                accountIdentifier = accountIdentifier,
            )
        )
    }

    /**
     * Seeds a balanced 2-entry transaction directly into the ledger tables so the V5 sum-to-zero
     * deferred trigger passes at commit: `-amountMinorUnits` on [fromAccountId] and
     * `+amountMinorUnits` on [toAccountId]. Raw SQL, not JPA — account-service no longer maps the
     * ledger entities; this drives the full-stack read tests whose bundled ledger-service reads the
     * rows back.
     */
    fun aBalancedLedgerEntryPair(
        fromAccountId: UUID,
        toAccountId: UUID,
        amountMinorUnits: Long,
        postedAt: Instant = Instant.parse("2025-10-15T10:00:00Z"),
        description: String? = null,
        transactionId: UUID = UUID.randomUUID(),
        initiatedById: UUID = UUID.randomUUID(),
        counterpartyId: UUID? = null,
        merchantName: String? = null,
        category: String? = null,
    ) = CallableAction {
        val jdbc = getBean(JdbcTemplate::class.java)
        val postedTimestamp = Timestamp.from(postedAt)
        jdbc.update(
            "INSERT INTO transactions (transaction_id, initiated_by_id, initiated_at, type, description) " +
                "VALUES (?, ?, ?, 'FIXTURE', ?)",
        ) { ps ->
            ps.setObject(1, transactionId)
            ps.setObject(2, initiatedById)
            ps.setTimestamp(3, postedTimestamp)
            if (description != null) ps.setString(4, description) else ps.setNull(4, Types.VARCHAR)
        }
        // Both rows in one statement so the deferred sum-to-zero trigger sees the balanced pair at
        // a single commit — these fixtures run outside any surrounding @Transactional boundary.
        jdbc.update(
            "INSERT INTO ledger_entries " +
                "(entry_id, transaction_id, account_id, amount_minor_units, posted_at, description, counterparty_id, merchant_name, category) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ) { ps ->
            bindLedgerEntry(ps, 0, transactionId, fromAccountId, -amountMinorUnits, postedTimestamp, description, counterpartyId, merchantName, category)
            bindLedgerEntry(ps, 9, transactionId, toAccountId, amountMinorUnits, postedTimestamp, description, counterpartyId, merchantName, category)
        }
    }

    private fun bindLedgerEntry(
        ps: PreparedStatement,
        offset: Int,
        transactionId: UUID,
        accountId: UUID,
        amountMinorUnits: Long,
        postedAt: Timestamp,
        description: String?,
        counterpartyId: UUID?,
        merchantName: String?,
        category: String?,
    ) {
        ps.setObject(offset + 1, UUID.randomUUID())
        ps.setObject(offset + 2, transactionId)
        ps.setObject(offset + 3, accountId)
        ps.setLong(offset + 4, amountMinorUnits)
        ps.setTimestamp(offset + 5, postedAt)
        if (description != null) ps.setString(offset + 6, description) else ps.setNull(offset + 6, Types.VARCHAR)
        if (counterpartyId != null) ps.setObject(offset + 7, counterpartyId) else ps.setNull(offset + 7, Types.OTHER)
        if (merchantName != null) ps.setString(offset + 8, merchantName) else ps.setNull(offset + 8, Types.VARCHAR)
        if (category != null) ps.setString(offset + 9, category) else ps.setNull(offset + 9, Types.VARCHAR)
    }

    // -------- state collectors / assertions --------

    fun theAccountInTheDatabase(
        accountUid: UUID,
    ) = StateCollector<AccountEntity> { _ ->
        accountRepository().findById(accountUid).orElseThrow {
            AssertionError("Account with UID $accountUid not found in database")
        }
    }

    /**
     * Returns the SORT_CODE + IBAN_BIC identifier rows for an account, sorted by enum ordinal
     * (SORT_CODE then IBAN_BIC) — matches how `AccountsService.getIdentifiers` renders the
     * public list, so assertions can rely on a stable order regardless of DB row ordering.
     */
    fun theIdentifiersInTheDatabase(
        accountUid: UUID,
    ) = StateCollector { _ ->
        accountIdentifierRepository().findByAccountId(accountUid)
            .sortedBy { it.identifierType.ordinal }
    }

    /**
     * Reads the `ledger_entries` rows for an account (newest-first) via raw SQL — used by the
     * full-stack tests to assert the bundled ledger-service persisted the expected number of entries.
     */
    fun theLedgerEntriesInTheDatabase(
        accountUid: UUID,
    ) = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForList(
            "SELECT entry_id, transaction_id, account_id, amount_minor_units, posted_at, " +
                "description, counterparty_id, merchant_name, category " +
                "FROM ledger_entries WHERE account_id = ? ORDER BY posted_at DESC",
            accountUid,
        )
    }
}

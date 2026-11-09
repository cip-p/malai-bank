package com.malai.accounts.support

import com.malai.accounts.entity.AccountType.*
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.banking.MalaiSortCode
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*

/**
 * Domain-fixture interface for the Plan 02-12 transfers slice. Mixed into the
 * happy + sad acceptance test classes so they share:
 *
 * - per-test 8-digit account numbers derived from `someAccountUid()` /
 *   `anotherAccountUid()` (so each test runs with a unique pair, avoiding the
 *   `account_identifiers (identifier_type, account_identifier)` UNIQUE constraint
 *   trip when tests share state inside a class)
 * - the JSON request-body builder
 * - the seed action that creates a Current account, a Savings account, two
 *   sort-code identifiers, and a SUSPENSE counterparty funded via a
 *   balanced ledger pair
 * - direct-JDBC state collectors for `audit_events` and `idempotency_keys`
 *   (these are generic enough that a future refactor could promote them to
 *   `malai-acceptance-test-commons`; today they're inlined here to keep the
 *   blast radius of Plan 02-12 small).
 */
interface WithTransfers : WithAccounts, MalaiTestFixtures {

    val malaiSortCode: String get() = MalaiSortCode.CANONICAL

    fun fromAccountNumber(): String = digitsFor(someAccountUid())
    fun toAccountNumber(): String = digitsFor(anotherAccountUid())

    private fun digitsFor(uuid: UUID): String =
        "%08d".format(Math.floorMod(uuid.mostSignificantBits, 100_000_000L))

    /**
     * Build a transfer request body as raw JSON. Used by the acceptance tests in
     * place of a typed builder because Plan 02-12's TransferRequest only ships
     * to one consumer (the webapp via openapi); a dedicated TestBuilder is
     * scope-creep until that consumer materialises in Plan 03.
     */
    fun transferBody(
        toAccountNumber: String,
        amountMinorUnits: Long,
        description: String? = null,
        toSortCode: String = malaiSortCode,
        currency: String = "GBP",
    ): String {
        val descLine = if (description != null) ""","description":"$description"""" else ""
        return """{"toSortCode":"$toSortCode","toAccountNumber":"$toAccountNumber","amountMinorUnits":$amountMinorUnits,"currency":"$currency"$descLine}"""
    }

    /**
     * Seeds a Current account (PRIMARY, the "from") and a Savings account (the "to"), both on the
     * same accountHolder, each with a SORT_CODE identifier so destination resolution by sort+number
     * works. The funds guard now lives in the ledger behind payments-service (stubbed in these
     * tests), so account-service no longer seeds or reads a starting balance for a transfer —
     * [startingBalanceMinor] is retained only to keep the call sites legible.
     */
    fun seededOwnAccountsWithStartingBalance(startingBalanceMinor: Long) = CallableAction {
        anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
            accountType = PRIMARY,
            name = "Personal",
        ).execute()
        anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = fromAccountNumber(),
        ).execute()

        anAccountExists(
            accountId = anotherAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
            accountType = SAVINGS,
            name = "Savings",
        ).execute()
        anIdentifierExistsForAccount(
            accountId = anotherAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = toAccountNumber(),
        ).execute()
    }

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    fun theAuditEventRowsInTheDatabase() = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForList(
            """
            SELECT seq, action, actor_id, actor_type, resource_type, resource_id,
                   after_state::text AS after_state, trace_id
            FROM audit_events
            ORDER BY seq ASC
            """.trimIndent(),
        )
    }

    fun theIdempotencyKeyResponseBody(key: String, service: String = "account-service") = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForObject(
            "SELECT encode(response_body, 'escape') FROM idempotency_keys WHERE service = ? AND key = ?",
            String::class.java,
            service,
            key,
        ) ?: ""
    }
}

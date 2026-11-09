package com.malai.ledger.support

import com.malai.commons.model.Currency
import com.malai.ledger.entity.HoldEntity
import com.malai.ledger.entity.LedgerAccountEntity
import com.malai.ledger.entity.LedgerEntryRowEntity
import com.malai.ledger.entity.LedgerTransactionEntity
import com.malai.ledger.model.BalancesResponse
import com.malai.ledger.model.HoldStatus
import com.malai.ledger.model.LedgerAccountKind
import com.malai.ledger.model.LedgerGuardPolicy
import com.malai.ledger.repository.HoldRepository
import com.malai.ledger.repository.LedgerAccountRepository
import com.malai.ledger.repository.LedgerEntryRowRepository
import com.malai.ledger.repository.LedgerTransactionRepository
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import java.time.Instant
import java.util.UUID

interface WithLedgerService : WithApplicationContext {

    fun ledgerAccountRepository(): LedgerAccountRepository = getBean(LedgerAccountRepository::class.java)
    fun transactionRepository(): LedgerTransactionRepository = getBean(LedgerTransactionRepository::class.java)
    fun entryRepository(): LedgerEntryRowRepository = getBean(LedgerEntryRowRepository::class.java)
    fun holdRepository(): HoldRepository = getBean(HoldRepository::class.java)

    fun thereIsNoLedgerData() = CallableAction {
        holdRepository().deleteAll()
        entryRepository().deleteAll()
        transactionRepository().deleteAll()
        ledgerAccountRepository().deleteAll()
    }

    fun aLedgerAccountIsRegistered(
        accountId: UUID,
        currency: Currency = Currency.GBP,
        kind: LedgerAccountKind = LedgerAccountKind.CUSTOMER,
        guardPolicy: LedgerGuardPolicy = LedgerGuardPolicy.NO_OVERDRAFT,
        floorMinorUnits: Long? = null,
    ) = CallableAction {
        ledgerAccountRepository().save(LedgerAccountEntity(accountId, currency, kind, guardPolicy, floorMinorUnits))
    }

    fun aSettledBalanceExists(
        accountId: UUID,
        amountMinorUnits: Long,
        fundingAccountId: UUID = UUID.randomUUID(),
        postedAt: Instant = Instant.parse("2026-10-01T08:00:00Z"),
        transactionId: UUID = UUID.randomUUID(),
    ) = CallableAction {
        transactionRepository().save(
            LedgerTransactionEntity(
                transactionId = transactionId,
                initiatedById = UUID.randomUUID(),
                initiatedAt = postedAt,
                type = "FIXTURE",
            )
        )
        entryRepository().saveAll(
            listOf(
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = transactionId,
                    accountId = fundingAccountId,
                    amountMinorUnits = -amountMinorUnits,
                    postedAt = postedAt,
                ),
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = transactionId,
                    accountId = accountId,
                    amountMinorUnits = amountMinorUnits,
                    postedAt = postedAt,
                ),
            )
        )
    }

    /**
     * Posts a balanced 2-entry transaction so the V5 sum-to-zero deferred trigger passes:
     * `-amountMinorUnits` on [fromAccountId] and `+amountMinorUnits` on [toAccountId]. Use this
     * to seed posted history for the entries projection — the per-entry metadata (description,
     * counterpartyId, merchantName, category) is opaque passthrough the projection echoes back.
     */
    fun aBalancedEntryPair(
        fromAccountId: UUID,
        toAccountId: UUID,
        amountMinorUnits: Long,
        postedAt: Instant = Instant.parse("2026-05-01T10:00:00Z"),
        description: String? = null,
        transactionId: UUID = UUID.randomUUID(),
        initiatedById: UUID = UUID.randomUUID(),
        counterpartyId: UUID? = null,
        merchantName: String? = null,
        category: String? = null,
        type: String = "FIXTURE",
        traceId: String? = null,
    ) = CallableAction {
        transactionRepository().save(
            LedgerTransactionEntity(
                transactionId = transactionId,
                initiatedById = initiatedById,
                initiatedAt = postedAt,
                type = type,
                description = description,
                traceId = traceId,
            )
        )
        entryRepository().saveAll(
            listOf(
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = transactionId,
                    accountId = fromAccountId,
                    amountMinorUnits = -amountMinorUnits,
                    postedAt = postedAt,
                    description = description,
                    counterpartyId = counterpartyId,
                    merchantName = merchantName,
                    category = category,
                ),
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = transactionId,
                    accountId = toAccountId,
                    amountMinorUnits = amountMinorUnits,
                    postedAt = postedAt,
                    description = description,
                    counterpartyId = counterpartyId,
                    merchantName = merchantName,
                    category = category,
                ),
            )
        )
    }

    /**
     * Seeds [count] balanced entry pairs of [amountMinorUnitsEach] each, ordered one second apart
     * starting at [firstPostedAt]. Used to verify the recent-entries cap on the projection without
     * exploding the test body with a `repeat` block (Kensa's parser doesn't tolerate arbitrary
     * nested lambdas inside `malaiAcceptanceTest { ... }`).
     */
    fun manyBalancedEntryPairs(
        fromAccountId: UUID,
        toAccountId: UUID,
        count: Int,
        amountMinorUnitsEach: Long,
        firstPostedAt: Instant,
    ) = CallableAction {
        val transactions = (0 until count).map { i ->
            LedgerTransactionEntity(
                transactionId = UUID.randomUUID(),
                initiatedById = UUID.randomUUID(),
                initiatedAt = firstPostedAt.plusSeconds(i.toLong()),
                type = "FIXTURE",
            )
        }
        transactionRepository().saveAll(transactions)
        val entries = transactions.flatMap { tx ->
            val postedAt = tx.initiatedAt
            listOf(
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = tx.transactionId,
                    accountId = fromAccountId,
                    amountMinorUnits = -amountMinorUnitsEach,
                    postedAt = postedAt,
                ),
                LedgerEntryRowEntity(
                    entryId = UUID.randomUUID(),
                    transactionId = tx.transactionId,
                    accountId = toAccountId,
                    amountMinorUnits = amountMinorUnitsEach,
                    postedAt = postedAt,
                ),
            )
        }
        entryRepository().saveAll(entries)
    }

    fun theLedgerAccountInTheDatabase(accountId: UUID) = StateCollector<LedgerAccountEntity> { _ ->
        ledgerAccountRepository().findById(accountId).orElseThrow {
            AssertionError("Ledger account $accountId not found in database")
        }
    }

    fun theLedgerAccountsInTheDatabase() = StateCollector { _ ->
        ledgerAccountRepository().findAll()
    }

    fun theTransactionInTheDatabase(transactionId: UUID) = StateCollector<LedgerTransactionEntity> { _ ->
        transactionRepository().findById(transactionId).orElseThrow {
            AssertionError("Transaction $transactionId not found in database")
        }
    }

    fun theTransactionsInTheDatabase() = StateCollector { _ ->
        transactionRepository().findAll()
    }

    fun theEntriesForTransaction(transactionId: UUID) = StateCollector { _ ->
        entryRepository().findByTransactionId(transactionId)
    }

    fun theEntriesInTheDatabase() = StateCollector { _ ->
        entryRepository().findAll()
    }

    fun aHoldExists(
        holdId: UUID,
        accountId: UUID,
        amountMinorUnits: Long,
        status: HoldStatus = HoldStatus.PLACED,
        placedAt: Instant = Instant.parse("2026-10-15T10:30:00Z"),
        resolvedAt: Instant? = null,
    ) = CallableAction {
        holdRepository().save(HoldEntity(holdId, accountId, amountMinorUnits, status, placedAt, resolvedAt))
    }

    fun theHoldInTheDatabase(holdId: UUID) = StateCollector<HoldEntity> { _ ->
        holdRepository().findById(holdId).orElseThrow {
            AssertionError("Hold $holdId not found in database")
        }
    }

    fun theHoldsForAccount(accountId: UUID) = StateCollector { _ ->
        holdRepository().findByAccountId(accountId)
    }

    fun theHoldsInTheDatabase() = StateCollector { _ ->
        holdRepository().findAll()
    }

    fun theBalancesForAccount(accountId: UUID) = StateCollector<BalancesResponse> { _ ->
        val ledgerBalance = entryRepository().ledgerBalanceOf(accountId)
        val heldAmount = holdRepository().heldAmountOf(accountId)
        BalancesResponse(accountId, ledgerBalance, heldAmount, ledgerBalance - heldAmount)
    }
}

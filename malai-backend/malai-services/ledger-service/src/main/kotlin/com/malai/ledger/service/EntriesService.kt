package com.malai.ledger.service

import com.malai.commons.exception.MalaiCode
import com.malai.commons.exception.NotFoundException
import com.malai.ledger.model.EntriesResponse
import com.malai.ledger.model.LedgerEntryProjection
import com.malai.ledger.model.TracedTransactionsResponse
import com.malai.ledger.model.TransactionEntriesResponse
import com.malai.ledger.repository.LedgerEntryProjectionRow
import com.malai.ledger.repository.LedgerEntryRowRepository
import com.malai.ledger.repository.LedgerTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Projects an account's ledger entries with a running balance on read — the running-balance window
 * query and statement opening/closing bounds the public face used to own. With no period the
 * projection returns the most recent [limit] entries (the transaction list); with a period it
 * returns the entries inside `[periodStart, periodEnd)` plus the opening/closing balances that
 * bracket it (the statement). A held-but-unsettled payment has no ledger entry, so it never
 * appears here; a returned payment never appears.
 */
@Service
class EntriesService(
    private val entryRepository: LedgerEntryRowRepository,
    private val transactionRepository: LedgerTransactionRepository,
) {

    @Transactional(readOnly = true)
    fun listEntries(accountId: UUID, periodStart: String?, periodEnd: String?, limit: Int): EntriesResponse {
        if (periodStart != null && periodEnd != null) {
            val start = Instant.parse(periodStart)
            val end = Instant.parse(periodEnd)
            return EntriesResponse(
                accountId = accountId,
                entries = entryRepository.findEntriesForAccountInPeriod(accountId, start, end).map { it.toProjection() },
                openingBalanceMinorUnits = entryRepository.balanceBefore(accountId, start),
                closingBalanceMinorUnits = entryRepository.balanceBefore(accountId, end),
            )
        }
        return EntriesResponse(
            accountId = accountId,
            entries = entryRepository.findRecentEntriesForAccount(accountId, limit).map { it.toProjection() },
        )
    }

    /**
     * Every entry of one transaction under the transaction's own facts, for a caller holding a
     * transaction id and needing the legs that explain it. Unlike the account-scoped projection
     * each entry names its own account, because the legs of a double entry differ only by account
     * and sign. An unknown transaction is a 404 rather than an empty list — a posted transaction
     * always has entries, so "no rows" can only mean the transaction does not exist.
     */
    @Transactional(readOnly = true)
    fun listTransactionEntries(transactionId: UUID): TransactionEntriesResponse {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { NotFoundException(MalaiCode.LEDGER_TRANSACTION_NOT_FOUND) }
        return TransactionEntriesResponse(
            transactionId = transaction.transactionId,
            postedAt = transaction.initiatedAt,
            type = transaction.type,
            description = transaction.description,
            entries = entryRepository.findEntriesForTransaction(transactionId)
                .map { it.toProjection(withAccountId = true) },
        )
    }

    @Transactional(readOnly = true)
    fun listTransactionsForTrace(traceId: String): TracedTransactionsResponse {
        val entriesByTransaction = entryRepository.findEntriesForTrace(traceId)
            .groupBy { it.getTransactionId() }
        return TracedTransactionsResponse(
            traceId = traceId,
            transactions = transactionRepository.findByTraceIdOrderByInitiatedAtAscTransactionIdAsc(traceId)
                .map { transaction ->
                    TransactionEntriesResponse(
                        transactionId = transaction.transactionId,
                        postedAt = transaction.initiatedAt,
                        type = transaction.type,
                        description = transaction.description,
                        entries = entriesByTransaction[transaction.transactionId]
                            .orEmpty()
                            .map { it.toProjection(withAccountId = true) },
                    )
                },
        )
    }

    private fun LedgerEntryProjectionRow.toProjection(withAccountId: Boolean = false) = LedgerEntryProjection(
        entryId = getEntryId(),
        transactionId = getTransactionId(),
        postedAt = getPostedAt(),
        amountMinorUnits = getAmountMinorUnits(),
        runningBalanceMinorUnits = getRunningBalanceMinorUnits(),
        description = getDescription(),
        counterpartyId = getCounterpartyId(),
        merchantName = getMerchantName(),
        category = getCategory(),
        transactionType = getTransactionType(),
        accountId = if (withAccountId) getAccountId() else null,
    )
}

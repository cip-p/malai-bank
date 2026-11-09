package com.malai.ledger.service

import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.LEDGER_ACCOUNT_NOT_REGISTERED
import com.malai.commons.exception.MalaiCode.LEDGER_INSUFFICIENT_FUNDS
import com.malai.commons.exception.MalaiCode.LEDGER_TRANSACTION_NOT_BALANCED
import com.malai.commons.exception.UnprocessableEntityException
import com.malai.ledger.entity.LedgerEntryRowEntity
import com.malai.ledger.entity.LedgerTransactionEntity
import com.malai.ledger.model.PostTransactionRequest
import com.malai.ledger.model.PostTransactionResponse
import com.malai.ledger.model.PostedLedgerEntryResponse
import com.malai.ledger.repository.HoldRepository
import com.malai.ledger.repository.LedgerAccountRepository
import com.malai.ledger.repository.LedgerEntryRowRepository
import com.malai.ledger.repository.LedgerTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The heart of the ledger: records one balanced [com.malai.ledger.entity.LedgerTransactionEntity]
 * (entries summing to zero) atomically and idempotently on the caller-supplied transaction id.
 *
 * In one DB transaction it: returns the original result on replay; rejects an unbalanced set;
 * locks the guarded accounts and rejects a posting that would take a guarded account's Available
 * balance below its per-account floor (LEDGER_INSUFFICIENT_FUNDS — 0 for NO_OVERDRAFT, the
 * registered minimum for FLOORED, unbounded for ALLOW_NEGATIVE); rejects posting to an
 * unregistered account (4xx); otherwise persists the transaction and its entries. The DB
 * sum-to-zero deferred trigger (V5) is the structural backstop beneath the application-level check.
 */
@Service
class PostingEngine(
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val transactionRepository: LedgerTransactionRepository,
    private val entryRepository: LedgerEntryRowRepository,
    private val holdRepository: HoldRepository,
    private val uuidGenerator: UuidGenerator,
) {

    @Transactional
    fun post(request: PostTransactionRequest): PostTransactionResponse {
        val transactionId = request.transactionId!!

        val existing = transactionRepository.findById(transactionId).orElse(null)
        if (existing != null) {
            return toResponse(
                transactionId,
                existing.initiatedAt,
                entryRepository.findByTransactionIdOrderByAmountMinorUnitsAsc(transactionId),
            )
        }

        val entries = request.entries
        if (entries.sumOf { it.amountMinorUnits!! } != 0L) {
            throw BadRequestException(LEDGER_TRANSACTION_NOT_BALANCED)
        }

        val lockedAccounts = entries
            .map { it.accountId!! }
            .distinct()
            .sorted()
            .associateWith { accountId ->
                ledgerAccountRepository.lockByAccountId(accountId)
                    ?: throw BadRequestException(LEDGER_ACCOUNT_NOT_REGISTERED)
            }

        entries
            .groupBy { it.accountId!! }
            .forEach { (accountId, accountEntries) ->
                val account = lockedAccounts.getValue(accountId)
                val floor = account.guardFloorMinorUnits
                if (floor != null) {
                    val delta = accountEntries.sumOf { it.amountMinorUnits!! }
                    val available = entryRepository.ledgerBalanceOf(accountId) -
                        holdRepository.heldAmountOf(accountId) + delta
                    if (available < floor) {
                        throw UnprocessableEntityException(LEDGER_INSUFFICIENT_FUNDS)
                    }
                }
            }

        val postedAt = Instant.parse(request.postedAt!!)

        transactionRepository.save(
            LedgerTransactionEntity(
                transactionId = transactionId,
                initiatedById = request.initiatedById!!,
                initiatedAt = postedAt,
                type = request.type!!,
                description = request.description,
                traceId = request.traceId,
            )
        )

        val savedEntries = entries.map { entry ->
            entryRepository.save(
                LedgerEntryRowEntity(
                    entryId = uuidGenerator.generate(),
                    transactionId = transactionId,
                    accountId = entry.accountId!!,
                    amountMinorUnits = entry.amountMinorUnits!!,
                    postedAt = postedAt,
                    description = entry.description,
                    counterpartyId = entry.counterpartyId,
                    merchantName = entry.merchantName,
                    category = entry.category,
                    traceId = request.traceId,
                )
            )
        }

        return toResponse(transactionId, postedAt, savedEntries)
    }

    private fun toResponse(
        transactionId: java.util.UUID,
        postedAt: Instant,
        entries: List<LedgerEntryRowEntity>,
    ) = PostTransactionResponse(
        transactionId = transactionId,
        postedAt = postedAt.toString(),
        entries = entries.map { PostedLedgerEntryResponse(it.entryId, it.accountId, it.amountMinorUnits) },
    )
}

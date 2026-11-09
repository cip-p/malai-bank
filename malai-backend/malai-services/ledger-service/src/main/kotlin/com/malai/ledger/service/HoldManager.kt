package com.malai.ledger.service

import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.LEDGER_ACCOUNT_NOT_REGISTERED
import com.malai.commons.exception.MalaiCode.LEDGER_HOLD_ALREADY_RESOLVED
import com.malai.commons.exception.MalaiCode.LEDGER_HOLD_NOT_FOUND
import com.malai.commons.exception.MalaiCode.LEDGER_INSUFFICIENT_FUNDS
import com.malai.commons.exception.MalaiCode.LEDGER_SETTLE_AMOUNT_INVALID
import com.malai.commons.exception.NotFoundException
import com.malai.commons.exception.UnprocessableEntityException
import com.malai.ledger.entity.HoldEntity
import com.malai.ledger.model.HoldResponse
import com.malai.ledger.model.HoldStatus.PLACED
import com.malai.ledger.model.HoldStatus.RELEASED
import com.malai.ledger.model.HoldStatus.SETTLED
import com.malai.ledger.model.PlaceHoldRequest
import com.malai.ledger.model.SettleHoldRequest
import com.malai.ledger.model.SettleHoldResponse
import com.malai.ledger.repository.HoldRepository
import com.malai.ledger.repository.LedgerAccountRepository
import com.malai.ledger.repository.LedgerEntryRowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Owns the Hold lifecycle (`PLACED -> SETTLED | RELEASED`, no expiry) and the
 * Available-balance arithmetic. A hold reserves an amount on a ledger account, reducing
 * its Available balance (ledger - held) without moving its Ledger balance.
 *
 * - [place] is funds-guarded on Available and idempotent on the hold id, taking the
 *   account lock so two concurrent holds can't reserve the same money.
 * - [settle] is atomic post-and-release: in one DB transaction the hold flips to SETTLED
 *   and the caller's balanced debit posting is recorded via the [PostingEngine], so
 *   Available never blips. An optional settle amount (0 < amount <= held) captures part of
 *   the reservation and releases the remainder; omitted settles the full held amount.
 *   Idempotent on the hold id (and the posting's transaction id).
 * - [release] flips the hold to RELEASED and records no ledger entries.
 * - [get] reads one hold back whole, for a caller reconstructing why an amount was reserved
 *   and whether it still is. An unknown hold is a 404 — a read is not an attempted mutation.
 */
@Service
class HoldManager(
    private val holdRepository: HoldRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val entryRepository: LedgerEntryRowRepository,
    private val postingEngine: PostingEngine,
) {

    @Transactional(readOnly = true)
    fun get(holdId: UUID): HoldResponse =
        holdRepository.findById(holdId).orElse(null)?.toResponse()
            ?: throw NotFoundException(LEDGER_HOLD_NOT_FOUND)

    @Transactional
    fun place(request: PlaceHoldRequest): HoldResponse {
        val holdId = request.holdId!!
        holdRepository.findById(holdId).orElse(null)?.let { return it.toResponse() }

        val account = ledgerAccountRepository.lockByAccountId(request.accountId!!)
            ?: throw BadRequestException(LEDGER_ACCOUNT_NOT_REGISTERED)
        val amount = request.amountMinorUnits!!

        val floor = account.guardFloorMinorUnits
        if (floor != null) {
            val available = entryRepository.ledgerBalanceOf(account.accountId) -
                holdRepository.heldAmountOf(account.accountId)
            if (available - amount < floor) {
                throw UnprocessableEntityException(LEDGER_INSUFFICIENT_FUNDS)
            }
        }

        val hold = holdRepository.save(
            HoldEntity(
                holdId = holdId,
                accountId = account.accountId,
                amountMinorUnits = amount,
                status = PLACED,
                placedAt = Instant.parse(request.placedAt!!),
                resolvedAt = null,
            )
        )
        return hold.toResponse()
    }

    @Transactional
    fun settle(holdId: UUID, request: SettleHoldRequest): SettleHoldResponse {
        val hold = holdRepository.findById(holdId).orElse(null)
            ?: throw BadRequestException(LEDGER_HOLD_NOT_FOUND)
        if (hold.status == RELEASED) {
            throw UnprocessableEntityException(LEDGER_HOLD_ALREADY_RESOLVED)
        }

        val resolved = if (hold.status == PLACED) {
            val settleAmount = request.amountMinorUnits
            if (settleAmount != null && (settleAmount <= 0 || settleAmount > hold.amountMinorUnits)) {
                throw BadRequestException(LEDGER_SETTLE_AMOUNT_INVALID)
            }
            holdRepository.save(hold.copy(status = SETTLED, resolvedAt = Instant.parse(request.postedAt!!)))
        } else {
            hold
        }

        val transaction = postingEngine.post(request.toPosting())

        return SettleHoldResponse(
            holdId = resolved.holdId,
            status = resolved.status,
            resolvedAt = resolved.resolvedAt!!.toString(),
            transaction = transaction,
        )
    }

    @Transactional
    fun release(holdId: UUID, resolvedAt: String): HoldResponse {
        val hold = holdRepository.findById(holdId).orElse(null)
            ?: throw BadRequestException(LEDGER_HOLD_NOT_FOUND)
        if (hold.status == SETTLED) {
            throw UnprocessableEntityException(LEDGER_HOLD_ALREADY_RESOLVED)
        }

        val resolved = if (hold.status == PLACED) {
            holdRepository.save(hold.copy(status = RELEASED, resolvedAt = Instant.parse(resolvedAt)))
        } else {
            hold
        }
        return resolved.toResponse()
    }

    private fun HoldEntity.toResponse() = HoldResponse(
        holdId = holdId,
        accountId = accountId,
        amountMinorUnits = amountMinorUnits,
        status = status,
        placedAt = placedAt.toString(),
        resolvedAt = resolvedAt?.toString(),
    )
}

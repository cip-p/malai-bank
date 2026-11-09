package com.malai.ledger.service

import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.LEDGER_ACCOUNT_FLOOR_INVALID
import com.malai.commons.model.Currency
import com.malai.ledger.entity.LedgerAccountEntity
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.ledger.repository.LedgerAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The ledger-account registry. An account must be registered here before anything can post to
 * it — the posting engine rejects unregistered accounts. Registration is idempotent on the
 * account id so `account-service.createAccount` (and seeding) is safe to replay.
 *
 * A `FLOORED` account carries a per-account funds-guard floor (minor units, typically negative —
 * the credit-card shape); the floor is required for `FLOORED` and normalised away for the other
 * policies, so the stored floor and the response never misrepresent the guard.
 */
@Service
class LedgerAccountRegistry(
    private val ledgerAccountRepository: LedgerAccountRepository,
) {

    @Transactional
    fun register(request: RegisterLedgerAccountRequest): LedgerAccountEntity {
        val accountId = request.accountId!!
        val existing = ledgerAccountRepository.findById(accountId).orElse(null)
        if (existing != null) {
            return existing
        }
        val guardPolicy = request.guardPolicy!!
        val floorMinorUnits = if (guardPolicy == FLOORED) {
            request.floorMinorUnits ?: throw BadRequestException(LEDGER_ACCOUNT_FLOOR_INVALID)
        } else {
            null
        }
        return ledgerAccountRepository.save(
            LedgerAccountEntity(
                accountId = accountId,
                currency = Currency.valueOf(request.currency!!),
                kind = request.kind!!,
                guardPolicy = guardPolicy,
                floorMinorUnits = floorMinorUnits,
            )
        )
    }
}

package com.malai.ledger.service

import com.malai.ledger.model.BalancesResponse
import com.malai.ledger.repository.HoldRepository
import com.malai.ledger.repository.LedgerEntryRowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Projects the three ledger-owned balances for an account on read — there is no mutable
 * balance column. Ledger balance = SUM(entries); Held amount = SUM(active holds);
 * Available balance = ledger - held. An in-flight outbound payment (a PLACED hold)
 * drops Available without moving the Ledger balance until it settles.
 */
@Service
class BalanceService(
    private val entryRepository: LedgerEntryRowRepository,
    private val holdRepository: HoldRepository,
) {

    @Transactional(readOnly = true)
    fun getBalances(accountId: UUID): BalancesResponse {
        val ledgerBalance = entryRepository.ledgerBalanceOf(accountId)
        val heldAmount = holdRepository.heldAmountOf(accountId)
        return BalancesResponse(
            accountId = accountId,
            ledgerBalanceMinorUnits = ledgerBalance,
            heldAmountMinorUnits = heldAmount,
            availableBalanceMinorUnits = ledgerBalance - heldAmount,
        )
    }
}

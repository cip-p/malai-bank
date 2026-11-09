package com.malai.accountholder.service

import com.malai.accountholder.model.AccountHoldersCreatedResponse
import com.malai.accountholder.repository.AccountHolderRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Counts account holders created in a window, for whichever caller is composing a day-scoped
 * figure. The window arrives as two instants rather than a date because the *meaning* of a day
 * belongs to the caller, not here — this context knows only when its rows were written.
 *
 * The count is of `account_holders` rows, never users: a staff-only login has a user and no
 * holder, and must never inflate the bank's customer count. Every holder type counts.
 */
@Service
class InternalAccountHolderMetricsService(
    private val accountHolderRepository: AccountHolderRepository,
) {

    fun accountHoldersCreated(from: Instant, until: Instant) = AccountHoldersCreatedResponse(
        accountHolderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, until)
    )
}

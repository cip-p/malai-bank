package com.malai.registrationseeder

import java.util.UUID

/**
 * Outcome of [RegistrationSeederService.seedFor]. Sealed so callers (HTTP
 * endpoint in S02, orchestrator hook in S03, CLI in T03) can exhaustively
 * branch on it without resorting to string `outcome` codes.
 */
sealed interface SeedResult {

    /**
     * `accountId` is the CURRENT account. The credit and savings fields are
     * null on the deterministic Sarah path, where malai-seeder's SeedCommand
     * owns those accounts, and on any re-seed where they already existed.
     */
    data class Seeded(
        val userId: UUID,
        val accountId: UUID,
        val transactionCount: Int,
        val openingBalanceMinor: Long,
        val completedForecastPostings: Int = 0,
        val creditAccountId: UUID? = null,
        val creditPostings: Int = 0,
        val savingsAccountId: UUID? = null,
        val pendingAuthorisations: Int = 0,
    ) : SeedResult

    data class Noop(
        val userId: UUID,
        val accountId: UUID,
        val existingTransactionCount: Int,
    ) : SeedResult

    data class UserNotFound(val email: String) : SeedResult

    data class NoActiveCurrentAccount(val userId: UUID) : SeedResult
}

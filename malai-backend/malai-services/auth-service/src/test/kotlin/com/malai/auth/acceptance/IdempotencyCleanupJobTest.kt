package com.malai.auth.acceptance

import com.malai.security.idempotency.IdempotencyCleanupJob
import com.malai.security.idempotency.IdempotencyKeyEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Integration test for [IdempotencyCleanupJob.runCleanup] (BACK-07). Piggybacks
 * on AuthAcceptanceTest's running Spring context + the local docker-compose
 * Postgres so the cleanup query runs against a real `idempotency_keys` table.
 *
 * Not a Kensa BDD acceptance test — there is no HTTP request to give/when/then;
 * the job is a `@Scheduled`-triggered DB-only operation. We prime a mix of
 * expired + still-valid rows, invoke the job directly, and assert the surviving
 * keys.
 */
class IdempotencyCleanupJobTest : AuthAcceptanceTest() {

    private val cleanupJob: IdempotencyCleanupJob get() = getBean(IdempotencyCleanupJob::class.java)

    @Test
    fun `runCleanup deletes only rows whose expires_at is strictly before now`() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-05-07T12:00:00Z"))

        val now = Instant.parse("2026-05-07T12:00:00Z")
        and(anIdempotencyKeyExists(
            key = "expired-1ms",
            createdAt = now.minusSeconds(86_400),
            expiresAt = now.minusMillis(1),
        ))
        and(anIdempotencyKeyExists(
            key = "expired-1d",
            createdAt = now.minusSeconds(2 * 86_400),
            expiresAt = now.minusSeconds(86_400),
        ))
        and(anIdempotencyKeyExists(
            key = "boundary-now",
            createdAt = now.minusSeconds(86_400),
            expiresAt = now,
        ))
        and(anIdempotencyKeyExists(
            key = "valid-1h",
            createdAt = now,
            expiresAt = now.plusSeconds(3_600),
        ))
        and(anIdempotencyKeyExists(
            key = "valid-24h",
            createdAt = now,
            expiresAt = now.plusSeconds(24 * 3_600),
        ))

        whenever(callableActionThatRunsCleanup())

        // Strict-less-than predicate keeps the boundary row whose expires_at == now.
        and(theIdempotencyKeysInTheDatabase()) {
            this.map(IdempotencyKeyEntity::key)
                .shouldContainExactlyInAnyOrder("boundary-now", "valid-1h", "valid-24h")
        }
    }

    private fun callableActionThatRunsCleanup() = dev.kensa.Action<dev.kensa.ActionContext> { _ ->
        cleanupJob.runCleanup()
    }
}

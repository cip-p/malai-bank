package com.malai.security.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {

    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKeyEntity i WHERE i.expiresAt < :now")
    fun deleteAllByExpiresAtBefore(now: Instant): Int
}

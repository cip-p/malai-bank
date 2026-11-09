package com.malai.security.idempotency

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class IdempotencyKeyInserter(
    @PersistenceContext private val em: EntityManager,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insert(entity: IdempotencyKeyEntity) {
        em.persist(entity)
        em.flush()
    }
}

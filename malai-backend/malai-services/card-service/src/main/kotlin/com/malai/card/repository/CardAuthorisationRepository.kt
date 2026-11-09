package com.malai.card.repository

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.CardAuthorisationStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CardAuthorisationRepository : JpaRepository<CardAuthorisationEntity, UUID> {
    fun findBySchemeReference(schemeReference: UUID): CardAuthorisationEntity?

    fun findByAuthorisationIdAndAccountHolderId(
        authorisationId: UUID,
        accountHolderId: UUID,
    ): CardAuthorisationEntity?

    fun findByAccountIdAndStatusInOrderByCreatedAtDesc(
        accountId: UUID,
        statuses: Collection<CardAuthorisationStatus>,
    ): List<CardAuthorisationEntity>
}

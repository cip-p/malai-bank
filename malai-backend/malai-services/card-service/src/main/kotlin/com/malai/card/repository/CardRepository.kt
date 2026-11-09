package com.malai.card.repository

import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface CardRepository : JpaRepository<CardEntity, UUID> {

    fun findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId: UUID): List<CardEntity>

    fun findByCardIdAndAccountHolderId(cardId: UUID, accountHolderId: UUID): CardEntity?

    fun findByAccountIdAndStatus(accountId: UUID, status: CardStatus): CardEntity?

    fun findByPan(pan: String): CardEntity?

    fun countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from: Instant, until: Instant): Long
}

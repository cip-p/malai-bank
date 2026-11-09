package com.malai.card.repository

import com.malai.card.entity.CardRefundEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CardRefundRepository : JpaRepository<CardRefundEntity, UUID> {
    fun existsByRefundReference(refundReference: UUID): Boolean
}

package com.malai.card.entity

import com.malai.card.model.CardStatus
import com.malai.card.model.CardVariant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cards")
data class CardEntity(

    @Id
    @Column(name = "card_id", nullable = false)
    val cardId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "account_holder_id", nullable = false)
    val accountHolderId: UUID,

    @Column(name = "cardholder_name", nullable = false)
    val cardholderName: String,

    @Column(name = "pan", nullable = false, length = 19)
    val pan: String,

    @Column(name = "expiry", nullable = false, length = 5)
    val expiry: String,

    @Column(name = "cvv", nullable = false, length = 4)
    val cvv: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "variant", nullable = false, length = 20)
    val variant: CardVariant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: CardStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

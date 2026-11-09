package com.malai.card.entity

import com.malai.card.model.CardAuthorisationStatus
import com.malai.card.model.DeclineReason
import com.malai.commons.model.Currency
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "card_authorisations")
data class CardAuthorisationEntity(

    @Id
    @Column(name = "authorisation_id", nullable = false)
    val authorisationId: UUID,

    @Column(name = "card_id")
    val cardId: UUID?,

    @Column(name = "account_id")
    val accountId: UUID?,

    @Column(name = "account_holder_id")
    val accountHolderId: UUID?,

    @Column(name = "scheme_reference", nullable = false)
    val schemeReference: UUID,

    @Column(name = "merchant_name", nullable = false, length = 255)
    val merchantName: String,

    @Column(name = "mcc", nullable = false, length = 4)
    val mcc: String,

    @Column(name = "amount_minor_units", nullable = false)
    val amountMinorUnits: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    val currency: Currency,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: CardAuthorisationStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason", length = 30)
    val declineReason: DeclineReason?,

    @Column(name = "hold_id")
    val holdId: UUID?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

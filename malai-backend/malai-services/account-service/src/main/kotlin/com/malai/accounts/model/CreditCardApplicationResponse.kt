package com.malai.accounts.model

import java.util.UUID

data class CreditCardApplicationResponse(
    val account: AccountResponse,
    val card: IssuedCardResponse,
)

data class IssuedCardResponse(
    val cardId: UUID,
    val accountId: UUID,
    val maskedPan: String,
    val variant: String,
    val status: String,
    val expiry: String,
)

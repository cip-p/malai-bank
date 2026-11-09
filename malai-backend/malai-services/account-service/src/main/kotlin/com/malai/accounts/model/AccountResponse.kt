package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.time.Instant
import java.util.*

data class AccountResponse(
    val accountUid: UUID,
    val accountType: String,
    val defaultCategory: UUID,
    val currency: Currency,
    val createdAt: Instant,
    val name: String
)

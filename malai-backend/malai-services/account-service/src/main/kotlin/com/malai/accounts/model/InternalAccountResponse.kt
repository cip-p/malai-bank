package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.util.UUID

data class InternalAccountResponse(
    val accountId: UUID,
    val accountHolderId: UUID,
    val currency: Currency,
    val sortCode: String,
    val accountNumber: String,
    val name: String,
)

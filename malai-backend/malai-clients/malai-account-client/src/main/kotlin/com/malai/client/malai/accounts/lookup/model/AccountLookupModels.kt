package com.malai.client.malai.accounts.lookup.model

import java.util.UUID

data class InternalAccountResponse(
    val accountId: UUID,
    val accountHolderId: UUID,
    val currency: String,
    val sortCode: String,
    val accountNumber: String,
    val name: String,
)

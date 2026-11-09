package com.malai.client.malai.accountholder.model

import java.util.*

data class AccountHolderSummaryResponse(
    val accountHolderUid: UUID,
    val accountHolderName: String,
    val accountHolderType: String
)

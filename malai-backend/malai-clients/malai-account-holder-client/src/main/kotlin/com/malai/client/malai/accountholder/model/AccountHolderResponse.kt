package com.malai.client.malai.accountholder.model

import java.util.*

data class AccountHolderResponse(
    val accountHolderUid: UUID,
    val accountHolderType: AccountHolderType
)

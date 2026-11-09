package com.malai.accounts.model

import com.malai.accounts.entity.AccountType
import com.malai.commons.model.Currency

data class CreateAccountRequest(
    val accountType: AccountType,
    val currency: Currency,
    val name: String
)

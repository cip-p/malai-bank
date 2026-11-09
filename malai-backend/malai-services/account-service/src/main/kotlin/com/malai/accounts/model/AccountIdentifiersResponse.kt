package com.malai.accounts.model

data class AccountIdentifiersResponse(
    val accountIdentifier: String?,
    val bankIdentifier: String?,
    val iban: String?,
    val bic: String?,
    val accountIdentifiers: List<AccountIdentifierResponse>
)

data class AccountIdentifierResponse(
    val identifierType: String,
    val bankIdentifier: String,
    val accountIdentifier: String
)

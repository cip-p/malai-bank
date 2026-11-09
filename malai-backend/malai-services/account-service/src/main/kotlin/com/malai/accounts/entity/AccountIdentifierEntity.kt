package com.malai.accounts.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "account_identifiers")
class AccountIdentifierEntity(

    @Id
    val identifierId: UUID,

    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    val identifierType: IdentifierType,

    val bankIdentifier: String,

    val accountIdentifier: String
)

enum class IdentifierType {
    SORT_CODE,
    IBAN_BIC,
    ABA_ACH
}

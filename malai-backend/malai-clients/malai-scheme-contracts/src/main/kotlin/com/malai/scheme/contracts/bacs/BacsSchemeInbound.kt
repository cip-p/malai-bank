package com.malai.scheme.contracts.bacs

import java.util.UUID

data class BacsSchemeParty(
    val sortCode: String,
    val accountNumber: String,
    val name: String,
)

data class BacsSchemeAmount(
    val minorUnits: Long,
    val currency: String,
)

data class BacsSchemeDirectCredit(
    val endToEndId: UUID,
    val creditor: BacsSchemeParty,
    val originator: BacsSchemeParty,
    val instructedAmount: BacsSchemeAmount,
    val remittanceInformation: String?,
)

data class BacsSchemeDirectDebitCollection(
    val endToEndId: UUID,
    val creditor: BacsSchemeParty,
    val debtor: BacsSchemeParty,
    val instructedAmount: BacsSchemeAmount,
    val remittanceInformation: String?,
)

data class BacsSchemeCollectionReturn(
    val endToEndId: UUID,
    val creditor: BacsSchemeParty,
    val reasonCode: String,
)

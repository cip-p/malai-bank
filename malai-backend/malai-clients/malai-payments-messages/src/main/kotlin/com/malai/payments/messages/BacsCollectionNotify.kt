package com.malai.payments.messages

import java.util.UUID

data class BacsCollectionNotify(
    val messageType: String = "bacs.collection.notify",
    val endToEndId: UUID,
    val debtorSortCode: String,
    val debtorAccountNumber: String,
    val debtorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val creditorName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
)

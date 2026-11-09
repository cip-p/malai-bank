package com.malai.payments.messages

import java.util.UUID

data class BacsCollectionReturn(
    val messageType: String = "bacs.collection.returned",
    val endToEndId: UUID,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val creditorName: String,
    val reason: ReturnReason,
)

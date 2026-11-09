package com.malai.client.malai.stub.collection.model

import java.util.UUID

data class CollectionForwardPayload(
    val debtorSortCode: String,
    val debtorAccountNumber: String,
    val debtorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val creditorName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
    val endToEndId: UUID,
)

package com.malai.client.malai.payments.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import java.util.UUID

@JsonInclude(NON_NULL)
data class InternalInitiatePaymentRequest(
    val endToEndId: UUID,
    val fromAccountId: UUID,
    val accountHolderId: UUID,
    val toSortCode: String? = null,
    val toAccountNumber: String? = null,
    val toName: String? = null,
    val amountMinorUnits: Long,
    val currency: String = "GBP",
    val reference: String? = null,
)

package com.malai.client.malai.payments.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import java.util.UUID

@JsonInclude(NON_NULL)
data class InitiatePaymentRequest(
    val fromAccountId: UUID,
    val toSortCode: String? = null,
    val toAccountNumber: String? = null,
    val toIban: String? = null,
    val toBic: String? = null,
    val paymentPurpose: String? = null,
    val toName: String? = null,
    val amountMinorUnits: Long,
    val currency: String = "GBP",
    val description: String? = null,
    val copVerdictOverride: Boolean = false,
)

package com.malai.client.malai.payments.model

import com.malai.payments.messages.ReturnReason
import java.util.UUID

data class InitiatePaymentResponse(
    val paymentId: UUID,
    val state: String,
    val rail: String,
    val direction: String,
    val createdAt: String,
    val endToEndId: UUID,
    val fromAccountId: UUID? = null,
    val fromSortCode: String? = null,
    val fromAccountNumber: String? = null,
    val fromName: String? = null,
    val toAccountId: UUID? = null,
    val toSortCode: String? = null,
    val toAccountNumber: String? = null,
    val toName: String? = null,
    val toIban: String? = null,
    val toBic: String? = null,
    val paymentPurpose: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String? = null,
    val returnReason: ReturnReason? = null,
    val fxConversion: FxConversion? = null,
) {
    data class FxConversion(
        val amountMinorUnits: Long,
        val currency: String,
        val rateUsed: String,
    )
}

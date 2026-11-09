package com.malai.payments.messages

import java.util.UUID

data class SwiftOutboundSubmit(
    val messageType: String = "swift.outbound.submit",
    val paymentId: UUID,
    val endToEndId: UUID,
    val debtorIban: String?,
    val debtorBic: String?,
    val debtorName: String?,
    val beneficiaryIban: String,
    val beneficiaryBic: String,
    val beneficiaryName: String?,
    val paymentPurpose: String?,
    val amountMinorUnits: Long,
    val currency: String,
    val targetAmountMinorUnits: Long,
    val targetCurrency: String,
    val description: String?,
)

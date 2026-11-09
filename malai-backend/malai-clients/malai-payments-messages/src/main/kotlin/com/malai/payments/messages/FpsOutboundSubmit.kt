package com.malai.payments.messages

import java.util.UUID

data class FpsOutboundSubmit(
    val messageType: String = "fps.outbound.submit",
    val paymentId: UUID,
    val endToEndId: UUID,
    val debtorSortCode: String?,
    val debtorAccountNumber: String?,
    val debtorName: String?,
    val beneficiarySortCode: String?,
    val beneficiaryAccountNumber: String?,
    val beneficiaryName: String?,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
    val copVerdictOverride: Boolean = false,
)

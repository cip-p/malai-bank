package com.malai.payments.messages

import java.util.UUID

sealed interface InboundNotify {
    val endToEndId: UUID
    val beneficiarySortCode: String
    val beneficiaryAccountNumber: String
    val beneficiaryName: String
    val originatorSortCode: String
    val originatorAccountNumber: String
    val originatorName: String
    val amountMinorUnits: Long
    val currency: String
    val description: String?
}
